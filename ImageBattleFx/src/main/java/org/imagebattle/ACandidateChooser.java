package org.imagebattle;

import java.io.File;
import java.io.IOException;
import java.util.Comparator;
import java.util.Date;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import javafx.util.Pair;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifSubIFDDirectory;

public abstract class ACandidateChooser {
	private static Logger log = LogManager.getLogger();

	protected TransitiveDiGraph2 graph2;

	public ACandidateChooser(TransitiveDiGraph2 pGraph) {
		graph2 = pGraph;
	}
	
	final int getCalculatedCandidateCount() {
		Set<File> vertexSet = graph2.vertexSet();
		int nodeCount = vertexSet.size();
		int maxEdgeCount = nodeCount * (nodeCount - 1) / 2;
		int currentEdgeCount = graph2.edgeSet().size();
		int calculatedCandidateCount = maxEdgeCount - currentEdgeCount;
		return calculatedCandidateCount;
	}

	final Pair<File, File> getNextCandidates() throws BattleFinishedException {
		Set<File> vertexSet = graph2.vertexSet();
		int calculatedCandidateCount = getCalculatedCandidateCount();
		
		if( calculatedCandidateCount == 0) {
			throw new BattleFinishedException();
		}

		return doGetNextCandidates();
	}

	abstract Pair<File, File> doGetNextCandidates();

	Stream<Pair<File, File>> getCandidateStream() {

		Set<File> vertexSet = graph2.vertexSet();
		Stream<Pair<File, File>> candidatesStream = null;

		// multiple choosing algorithms will need the candidates
		candidatesStream = graph2.vertexSet().stream() //
				.flatMap(from -> vertexSet.stream() //
						.filter(to -> !graph2.containsEdge(from, to)) // TODO double check necessary? overwrite maybe
						.filter(to -> !graph2.containsEdge(to, from)) //
						.filter(to -> !Objects.equals(to, from)) // graph does not allow loops
						.filter(to -> Comparator.comparing(File::getAbsolutePath).compare(to, from) > 0) // avoid having candidates a-b and b-a
						.map(to -> new Pair<File, File>(from, to)));
		return candidatesStream;
	}

	final Date readExif(File pFile) {
		Date result = null;

		try {
			Metadata metadata = ImageMetadataReader.readMetadata(pFile);
			// obtain the Exif directory
			Date date = Optional.ofNullable(metadata)//
					.map(meta -> meta.getFirstDirectoryOfType(ExifSubIFDDirectory.class))//
					.map(dir -> dir.getDate(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL))//
					.orElse(new Date(pFile.lastModified()));

			// query the tag's value
			log.trace(pFile.getAbsolutePath() + " : " + date);

			result = date;
		} catch (ImageProcessingException | IOException e) {
			log.error("exception", e);
		}
		return result;
	}

}
