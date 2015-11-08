package org.imagebattle.chooser;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.Objects;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.imagebattle.BattleFinishedException;
import org.imagebattle.TransitiveDiGraph2;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifSubIFDDirectory;

import javafx.util.Pair;

/**
 * Base class for all candidate choosers.
 * 
 * A candidate Chooser is an alogrithm that can find missing edges in the graph and follows a certain strategy.
 * 
 * Subclasses only need to implement {@link #doGetNextCandidates()}.
 * 
 * @author KoaGex
 *
 */
public abstract class ACandidateChooser {

	private static final Logger LOG = LogManager.getLogger();

	/**
	 * Use this graph. It is never null.
	 */
	protected final TransitiveDiGraph2 graph;

	/**
	 * Constructor.
	 * 
	 * @param pGraph
	 *            Subclasses will use this graph to determine the next candidates.
	 */
	ACandidateChooser(TransitiveDiGraph2 pGraph) {
		graph = Objects.requireNonNull(pGraph);
	}

	/**
	 * Hook. This is the only method that should be overridden by subclasses. This should not be used directly from the
	 * outside. Who ever uses a candidate chooser should call {@link #getNextCandidates()}.
	 * 
	 * @return any available candidate pair.
	 * @throws BattleFinishedException
	 *             when no candidates are left. TODO should not only {@link #doGetNextCandidates()} throw this?
	 */
	abstract Pair<File, File> doGetNextCandidates();

	/**
	 * @return {@link Optional#empty()} if there are no more candidates in the graph. This means the battle is finished.
	 */
	public final Optional<Pair<File, File>> getNextCandidates() {
		int calculatedCandidateCount = graph.getCalculatedCandidateCount();

		return (calculatedCandidateCount == 0) ? Optional.empty() : Optional.of(doGetNextCandidates());
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
			LOG.trace(pFile.getAbsolutePath() + " : " + date);

			result = date;
		} catch (ImageProcessingException | IOException e) {
			LOG.error("exception", e);
		}
		return result;
	}

}
