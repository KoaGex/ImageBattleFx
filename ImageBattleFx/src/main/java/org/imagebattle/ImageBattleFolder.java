package org.imagebattle;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.imagebattle.chooser.ACandidateChooser;
import org.imagebattle.chooser.BiSectionCandidateChooser;
import org.imagebattle.chooser.ChronologicKoCandidateChooser;
import org.imagebattle.chooser.DateDistanceCandidateChooser;
import org.imagebattle.chooser.MaxNewEdgesCandidateChoser;
import org.imagebattle.chooser.MinimumDegreeCandidateChooser;
import org.imagebattle.chooser.RandomCandidateChooser;
import org.imagebattle.chooser.RankingTopDownCandidateChooser;
import org.imagebattle.chooser.SameWinLoseRationCandidateChooser;
import org.imagebattle.chooser.WinnerOrientedCandidateChooser;

import javafx.util.Pair;

/**
 * Represents one folder the user has chosen. Files that are direct or indirect children of this directory take part in
 * the battle.
 * 
 * @author Besitzer
 *
 */
public class ImageBattleFolder implements Serializable {
	static final String IMAGE_BATTLE_DAT = "imageBattle.dat";
	private static final String IMAGE_BATTLE_RECURSIVE_DAT = "imageBattleRecursive.dat";

	private static Logger log = LogManager.getLogger();

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	final TransitiveDiGraph2 graph2;

	File datFile;
	private Set<File> ignoredFiles;

	/**
	 * Multiple strategies to select the next images to be compared.
	 */
	private transient Map<String, ACandidateChooser> choosingAlgorithms = new HashMap<>();
	private transient ACandidateChooser choosingAlgorithm;

	private boolean finished = false;

	/**
	 * private Constructor, it should only be created by {@link #readOrCreate(File)}.
	 * 
	 * @param chosenDirectory
	 * @param fileRegex
	 * @param recursive
	 */
	private ImageBattleFolder(File chosenDirectory, Predicate<File> fileRegex, Boolean recursive) {
		String datFileName = recursive ? IMAGE_BATTLE_RECURSIVE_DAT : IMAGE_BATTLE_DAT;
		datFile = new File(chosenDirectory.getAbsolutePath() + File.separator + datFileName);

		// TODO handle directory without images chosen
		LinkedList<File> currentLevel = new LinkedList<>();

		if (recursive) {
			LinkedList<File> queue = new LinkedList<>();
			queue.add(chosenDirectory);
			while (!queue.isEmpty()) {
				File currentDirectory = queue.removeFirst();

				File[] subdirectories = currentDirectory.listFiles();
				if (subdirectories != null) {
					List<File> asList = Arrays.asList(subdirectories);
					Map<Boolean, List<File>> partition = asList.stream()
							.collect(Collectors.partitioningBy(File::isDirectory));

					// add directories to queue
					queue.addAll(partition.get(Boolean.TRUE));

					// add matching files to current level
					partition.get(Boolean.FALSE).stream()//
							.filter(fileRegex)//
							.forEach(currentLevel::add);
				}

			}

		} else {
			File[] allFiles = chosenDirectory.listFiles();
			if (allFiles != null) {
				for (File aFile : allFiles) {
					if (fileRegex.test(aFile)) {
						currentLevel.add(aFile);
					}
				}
			}
		}

		log.debug("file count:" + currentLevel.size());
		graph2 = new TransitiveDiGraph2(currentLevel);

	}

	public static ImageBattleFolder readOrCreate(File chosenDirectory, Predicate<File> fileRegex, Boolean recursive) {
		log.info("chosenDirectory: {}", chosenDirectory);
		ImageBattleFolder result = null;

		String datFileName = recursive ? IMAGE_BATTLE_RECURSIVE_DAT : IMAGE_BATTLE_DAT;
		File datFile = new File(chosenDirectory.getAbsolutePath() + File.separator + datFileName);
		if (datFile.exists()) {
			ObjectInputStream ois;
			try {
				ois = new ObjectInputStream(new FileInputStream(datFile));
				Object obj = ois.readObject();
				result = (ImageBattleFolder) obj;
				ois.close();
				log.info("read success");
			} catch (IOException e) {
				log.warn("IO", e);
			} catch (ClassNotFoundException e) {
				log.warn("ClassNotFound", e);
			}
		}

		if (result == null) {
			log.info("create new");
			result = new ImageBattleFolder(chosenDirectory, fileRegex, recursive);
		} else {

			// search for new images and add them
			File[] listFiles = chosenDirectory.listFiles();
			if (listFiles != null) {
				List<File> currentFiles = new LinkedList<>(Arrays.asList(listFiles));
				log.info("currentFiles size:" + currentFiles.size());

				// TODO use regex
				// supported formats :BMP, GIF, JPEG, PNG
				currentFiles.removeIf(file -> {
					boolean isDirectory = file.isDirectory();
					String fileName = file.getName();
					boolean removeIfResult = isDirectory || !fileRegex.test(file);
					log.trace("matches: {}      \t    \tfile: {}", !removeIfResult, fileName);
					return removeIfResult;
				});

				Set<File> oldNodes = result.graph2.vertexSet();
				int oldEdgeSize = result.graph2.edgeSet().size();
				log.info("oldNodes size: {}   oldEdges size: {}", oldNodes.size(), oldEdgeSize);
				currentFiles.removeAll(oldNodes);

				currentFiles.forEach(result.graph2::addVertex);

				// add new images to graph2
				final ImageBattleFolder tempResult = result; // for effectively
				// final in lambda
				currentFiles.forEach(file -> {
					boolean wasAdded = tempResult.graph2.addVertex(file);
					if (wasAdded) {
						log.info("new file added: {}", file);
					}
				});

			}
		}

		// Merge in vertexes and edges from CentralStorage.
		TransitiveDiGraph2 readGraph = CentralStorage.readGraph(chosenDirectory, fileRegex, recursive);
		readGraph.vertexSet().forEach(result.graph2::addVertex);
		TransitiveDiGraph2 resultGraph = result.graph2;
		readGraph.edgeSet().forEach(edge -> {
			File edgeSource = readGraph.getEdgeSource(edge);
			File edgeTarget = readGraph.getEdgeTarget(edge);
			resultGraph.addEdge(edgeSource, edgeTarget);
		});

		if (result.ignoredFiles == null) {
			result.ignoredFiles = new HashSet<>();
		}

		// Merge in ignored files from CentralStorage.
		Set<File> readIgnoreFile = CentralStorage.readIgnoreFile(chosenDirectory, fileRegex, recursive);
		result.ignoredFiles.addAll(readIgnoreFile);

		// choosing algorithms
		if (result.choosingAlgorithms == null) {
			result.choosingAlgorithms = new HashMap<>();
		}
		Map<String, ACandidateChooser> newChoosingAlorithms = result.choosingAlgorithms;
		WinnerOrientedCandidateChooser winnerOriented = new WinnerOrientedCandidateChooser(result.graph2);
		newChoosingAlorithms.put("Winner Oriented", winnerOriented);
		newChoosingAlorithms.put("Date Distance", new DateDistanceCandidateChooser(result.graph2));
		newChoosingAlorithms.put("Chronologic KO", new ChronologicKoCandidateChooser(result.graph2));
		newChoosingAlorithms.put("Random", new RandomCandidateChooser(result.graph2));
		newChoosingAlorithms.put("MaxNewEdges", new MaxNewEdgesCandidateChoser(result.graph2));
		newChoosingAlorithms.put("RankingTopDown", new RankingTopDownCandidateChooser(result.graph2));
		newChoosingAlorithms.put("BiSection", new BiSectionCandidateChooser(result.graph2));
		newChoosingAlorithms.put("MinimumDegree", new MinimumDegreeCandidateChooser(result.graph2));
		SameWinLoseRationCandidateChooser sameWinLoseRatio = new SameWinLoseRationCandidateChooser(result.graph2);
		newChoosingAlorithms.put("SameWinLoseRatio", sameWinLoseRatio);

		result.choosingAlgorithm = sameWinLoseRatio;

		return result;
	}

	public List<ResultListEntry> getResultList() {

		// Comparator<File> winnerFirstComparator =
		// Comparator.comparing(graph2::outDegreeOf).reversed()
		// .thenComparing(Comparator.comparing(graph2::inDegreeOf));
		Comparator<File> winnerFirstComparator = Comparator.comparing(graph2::getWinLoseDifference,
				Comparator.reverseOrder());
		List<ResultListEntry> resultList = graph2.vertexSet().stream() //
				.sorted(winnerFirstComparator)//
				.map(graph2::fileToResultEntry)//
				.collect(Collectors.toList());
		// zip would be cool
		for (int i = 0; i < resultList.size(); i++) {
			resultList.get(i).place = i + 1;
		}

		// at the end append all ignored files. The user should to see them to
		// maybe un-ignore or delete them.
		ignoredFiles.stream().map(file -> {
			ResultListEntry resultListEntry = new ResultListEntry();
			resultListEntry.file = file;
			resultListEntry.ignored = true;
			return resultListEntry;
		}).forEach(resultList::add);

		return resultList;
	}

	public void makeDecision(File pWinner, File pLoser) {
		graph2.addEdge(pWinner, pLoser);
	}

	public Set<String> getChoosingAlgorithms() {
		return choosingAlgorithms.keySet();
	}

	public void setChoosingAlgorithm(String pAlgorithmName) {
		ACandidateChooser newAlgorithm = choosingAlgorithms.get(pAlgorithmName);
		Objects.requireNonNull(newAlgorithm);
		choosingAlgorithm = newAlgorithm;
		log.info(pAlgorithmName);
	}

	public Pair<File, File> getNextToCompare() throws BattleFinishedException {

		Pair<File, File> nextToCompare = null;

		// loop until we find two existing images
		boolean bothExist = false;
		while (!bothExist) {
			// TODO what if this loop removes all nodes ?!

			long start = System.currentTimeMillis();
			nextToCompare = choosingAlgorithm.getNextCandidates();
			long duration = System.currentTimeMillis() - start;
			log.trace("time needed to find next pair: {} ms", duration);

			// check for images that have been deleted in the meantime
			File key = nextToCompare.getKey();
			File value = nextToCompare.getValue();

			log.trace("nextToCompare: key={}    value={}", key.getName(), value.getName());

			bothExist = true;
			if (!key.exists()) {
				graph2.removeVertex(key);
				bothExist = false;
			}
			if (!value.exists()) {
				graph2.removeVertex(value);
				bothExist = false;
			}

		}

		return nextToCompare;
	}

	void save() {
		CentralStorage.save(graph2, ignoredFiles);
	}

	void ignoreFile(File file) {
		ignoredFiles.add(file);
		log.info("added file {}. ", file);
		log.trace(" Now on ignore: {}", ignoredFiles);
		graph2.removeVertex(file);
	}

	double getProgress() {
		int maxEdgeCount = graph2.getMaxEdgeCount();
		int currentEdgeCount = graph2.getCurrentEdgeCount();
		return Double.valueOf(currentEdgeCount) / Double.valueOf(maxEdgeCount);
	}

	/**
	 * @param pFileToReset
	 *            remove this file from the graph and then add it again to remove all edges to and from the file.
	 */
	void reset(File pFileToReset) {
		graph2.removeVertex(pFileToReset);
		graph2.addVertex(pFileToReset);
	}

	boolean isFinished() {
		return finished;
	}

	void setFinished(boolean finished) {
		this.finished = finished;
	}

}
