package org.imagebattle;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
 * @author Besitzer
 *
 */
public class ImageBattleFolder implements Serializable {
    static final String IMAGE_BATTLE_DAT = "imageBattle.dat";

    private static Logger log = LogManager.getLogger();

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    private final TransitiveDiGraph2 graph2;

    private int humanDecisionCount = 0;
    File datFile;
    private Set<File> ignoredFiles; // FIXME continue

    /**
     * Multiple strategies to select the next images to be compared.
     */
    private transient Map<String, ACandidateChooser> choosingAlgorithms = new HashMap<>();
    private transient ACandidateChooser choosingAlgorithm;

    /**
     * private Constructor, it should only be created by
     * {@link #readOrCreate(File)}.
     * 
     * @param chosenDirectory
     * @param fileRegex
     */
    private ImageBattleFolder(File chosenDirectory, String fileRegex) {
	datFile = new File(chosenDirectory.getAbsolutePath() + File.separator + IMAGE_BATTLE_DAT);

	File[] allFiles = chosenDirectory.listFiles();
	// TODO handle directory without images chosen
	// TODO support more image formats, png.
	// TODO possible to show moving gifs?
	LinkedList<File> currentLevel = new LinkedList<>();
	Pattern pattern = Pattern.compile(fileRegex);
	for (File aFile : allFiles) {
	    if (pattern.matcher(aFile.getName().toUpperCase()).matches()) {
		currentLevel.add(aFile);
	    }
	}
	log.debug("file count:" + currentLevel.size());
	graph2 = new TransitiveDiGraph2(currentLevel);

    }

    public static ImageBattleFolder readOrCreate(File chosenDirectory, String fileRegex) {
	log.info("chosenDirectory: {}", chosenDirectory);
	ImageBattleFolder result = null;

	File datFile = new File(chosenDirectory.getAbsolutePath() + File.separator + IMAGE_BATTLE_DAT);
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
	    result = new ImageBattleFolder(chosenDirectory, fileRegex);
	} else {
	    // search for new images and add them
	    List<File> currentFileArray = Arrays.asList(chosenDirectory.listFiles());
	    List<File> currentFiles = new LinkedList<>(currentFileArray);
	    log.info("currentFiles size:" + currentFiles.size());

	    // TODO use regex
	    // supported formats :BMP, GIF, JPEG, PNG
	    Pattern pattern = Pattern.compile(".*\\.(BMP|GIF|JPEG|JPG|PNG)");
	    currentFiles.removeIf(file -> {
		boolean isDirectory = file.isDirectory();
		String fileName = file.getName();
		Matcher matcher = pattern.matcher(fileName.toUpperCase());
		boolean removeIfResult = isDirectory || !matcher.matches();
		log.trace("matches: {}      \t    \tfile: {}", !removeIfResult, fileName);
		return removeIfResult;
	    });

	    Set<File> oldNodes = result.graph2.vertexSet();
	    log.info("oldNodes size:" + oldNodes.size());
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

	if (result.ignoredFiles == null) {
	    result.ignoredFiles = new HashSet<>();
	}

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
	newChoosingAlorithms.put("SameWinLoseRatio", new SameWinLoseRationCandidateChooser(result.graph2));

	result.choosingAlgorithm = winnerOriented;

	return result;
    }

    ResultListEntry fileToResultEntry(File i) {
	ResultListEntry entry = new ResultListEntry();
	entry.file = i;
	entry.wins = graph2.outDegreeOf(i);
	entry.loses = graph2.inDegreeOf(i);
	entry.fixed = graph2.vertexSet().size() - 1 == entry.wins + entry.loses;
	return entry;
    }

    public List<ResultListEntry> getResultList() {

	// Comparator<File> winnerFirstComparator =
	// Comparator.comparing(graph2::outDegreeOf).reversed()
	// .thenComparing(Comparator.comparing(graph2::inDegreeOf));
	Comparator<File> winnerFirstComparator = Comparator.comparing(graph2::getWinLoseDifference,
		Comparator.reverseOrder());
	List<ResultListEntry> resultList = graph2.vertexSet().stream() //
		.sorted(winnerFirstComparator)//
		.map(this::fileToResultEntry)//
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
	humanDecisionCount++;
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
	try {
	    FileOutputStream fileOutputStream = new FileOutputStream(datFile);
	    ObjectOutputStream objectOutputStream = new ObjectOutputStream(fileOutputStream);
	    objectOutputStream.writeObject(this);
	    objectOutputStream.close();
	} catch (Exception e) {
	    e.printStackTrace();
	}
    }

    /**
     * Count of decisions to give the user a feeling about how much time was
     * invested into battle.
     */
    int getHumanDecisionCount() {
	return humanDecisionCount;
    }

    void ignoreFile(File file) {
	ignoredFiles.add(file);
	log.info("added file {}. Now on ignore: {}", file, ignoredFiles);
	graph2.removeVertex(file);
    }

    double getProgress() {
	int maxEdgeCount = graph2.getMaxEdgeCount();
	int currentEdgeCount = graph2.getCurrentEdgeCount();
	return Double.valueOf(currentEdgeCount) / Double.valueOf(maxEdgeCount);
    }

    /**
     * @param pFileToReset
     *            remove this file from the graph and then add it again to
     *            remove all edges to and from the file.
     */
    void reset(File pFileToReset) {
	graph2.removeVertex(pFileToReset);
	graph2.addVertex(pFileToReset);
    }
}
