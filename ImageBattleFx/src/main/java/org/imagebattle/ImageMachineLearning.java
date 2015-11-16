package org.imagebattle;

import java.awt.Dimension;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.IntSummaryStatistics;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.DoubleUnaryOperator;
import java.util.function.Function;
import java.util.function.IntToDoubleFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.ScrollPaneConstants;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.encog.engine.network.activation.ActivationFunction;
import org.encog.engine.network.activation.ActivationSigmoid;
import org.encog.ml.MLCluster;
import org.encog.ml.data.MLData;
import org.encog.ml.data.MLDataPair;
import org.encog.ml.data.basic.BasicMLData;
import org.encog.ml.kmeans.KMeansClustering;
import org.encog.ml.train.BasicTraining;
import org.encog.neural.networks.BasicNetwork;
import org.encog.neural.networks.layers.BasicLayer;
import org.encog.neural.networks.training.propagation.manhattan.ManhattanPropagation;
import org.encog.neural.networks.training.propagation.quick.QuickPropagation;
import org.encog.neural.networks.training.propagation.scg.ScaledConjugateGradient;
import org.encog.neural.networks.training.propagation.sgd.StochasticGradientDescent;
import org.encog.persist.EncogDirectoryPersistence;
import org.encog.platformspecific.j2se.data.image.ImageMLData;
import org.encog.platformspecific.j2se.data.image.ImageMLDataSet;
import org.encog.util.downsample.RGBDownsample;
import org.encog.util.simple.EncogUtility;

import javafx.embed.swing.SwingFXUtils;
import javafx.util.Pair;

/**
 * For testing encog.
 * 
 * VM Arguments: -Xmx8000M
 * 
 * I know 8 gb is a little bit crazy, need to work on that.
 * 
 * 
 * https://en.wikipedia.org/wiki/Learning_to_rank https://en.wikipedia.org/wiki/Recommender_system
 * 
 * @author KoaGex
 *
 */
public class ImageMachineLearning {

	private static final Logger LOG = LogManager.getLogger();
	// TODO create a method and then use these parameters as parameters
	private static final int ITERATION_COUNT = 50;

	private static final int HIDDEN_LAYER_COUNT = 1;

	private static final int DOWNSAMPLE_SIZE = 8;

	private static final long MAX_IMAGE_COUNT = 30;

	/**
	 * 
	 * @param args
	 */
	public static void main(String[] args) {
		// learnImageRating(MAX_IMAGE_COUNT, DOWNSAMPLE_SIZE, HIDDEN_LAYER_COUNT, ITERATION_COUNT);
		kMeans();
	}

	private static void kMeans() {
		/*
		 * How can kMeans be used for image battle?
		 * create clusters and rank the clusters?
		 */

		int maxImageCount = 300;
		int clusterCount = (int) Math.floor(Math.sqrt(maxImageCount));
		LOG.info("imageCount: {}   clusterCount:  {}", maxImageCount, clusterCount);

		boolean theFindBounds = false;
		ImageMLDataSet set = new ImageMLDataSet(new RGBDownsample(), theFindBounds, 255, 0);

		int readSize = 300;
		Function<File, ImageFileMLData> readImageFileToData = file -> new ImageFileMLData(
				ImageMachineLearning.read(file, readSize), file);

		Set<ResultListEntry> imageResultSetEntries = loadMostFightCountImages(maxImageCount);
		imageResultSetEntries.stream()//
				.map(entry -> entry.file)//
				.map(readImageFileToData)//
				.forEach(set::add);

		LOG.info("begin downsampling");
		set.downsample(200, 200);

		KMeansClustering kmeans = new KMeansClustering(clusterCount, set);

		LOG.info("begin iterations");
		kmeans.iteration(100);
		LOG.info("finished iterations");
		// System.out.println("Final WCSS: " + kmeans.getWCSS());

		// Display the cluster
		int i = 1;
		JFrame jFrame = new JFrame("Cluster " + i);
		JScrollPane jsp = new JScrollPane();
		jsp.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
		// jsp.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		jFrame.add(jsp);
		JPanel topPanel = new JPanel();
		topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));
		JPanel clusterPanel = new JPanel();
		clusterPanel.setMaximumSize(new Dimension(1900, 100000));
		jFrame.setMaximumSize(new Dimension(1900, 100000));
		for (MLCluster cluster : kmeans.getClusters()) {
			System.out.println("*** Cluster " + (i) + " ***");
			List<MLData> data = cluster.getData();
			System.out.println(data.size());

			clusterPanel.add(new JLabel("cluster " + i));
			for (MLData mlData : data) {
				ImageFileMLData imageData = (ImageFileMLData) mlData;
				Image image = imageData.getImage();
				ImageIcon imageIcon = new ImageIcon(image);

				clusterPanel.add(new JLabel(imageIcon));
				// LayoutManager manager = new FlowLayout();
				// jFrame.setLayout(manager);
				System.out.println(imageData.getFile().getAbsolutePath());

				// how to get from the data back to the file?
			}
			topPanel.add(clusterPanel);

			i++;

			// MLDataSet ds = cluster.createDataSet();
			// MLDataPair pair = BasicMLDataPair.createPair(ds.getInputSize(), ds.getIdealSize());
			// for (int j = 0; j < ds.getRecordCount(); j++) {
			// ds.getRecord(j, pair);
			// System.out.println(Arrays.toString(pair.getInputArray()));
			// }

		}
		JViewport viewport = jsp.getViewport();
		viewport.add(topPanel);
		jFrame.setSize(1900, 1000);
		jFrame.setVisible(true);
	}

	private static Set<ResultListEntry> loadMostFightCountImages(long maxImageCount) {
		CentralStorage centralStorage = new CentralStorage(CentralStorage.GRAPH_FILE, CentralStorage.IGNORE_FILE);
		TransitiveDiGraph imageGraph = centralStorage.readGraph(new File("D:\\"), ImageBattleApplication.imagePredicate,
				true);

		Function<? super ResultListEntry, ? extends Integer> entryToFightCount = entry -> entry.wins + entry.loses;

		Set<ResultListEntry> resultEntrys = imageGraph.vertexSet().stream()//
				.map(imageGraph::fileToResultEntry)//
				// sort: prefere images that fought many battles.
				.sorted(Comparator.comparing(entryToFightCount, Comparator.reverseOrder()))//
				.limit(maxImageCount) // to avoid heap space error
				// .peek(x -> System.err.println(entryToFightCount.apply(x) + "
				// " + x.wins))//
				.collect(Collectors.toSet());

		LOG.debug("added {} graph files", resultEntrys.size());

		return resultEntrys;
	}

	private static void learnImageRating(long maxImageCount, int downsampleSize, int hiddenLayerCount,
			int iterationCount) {
		/*
		 *  idea 1 : binary classifier ( ignore or not? )
		 *  I think this is difficult. Don't do it yet.
		 */

		LOG.info("start");
		RGBDownsample downSampler = new RGBDownsample();
		boolean findBounds = false;
		double downSampleHigh = 1;
		double downSampleLow = 0;

		Set<ResultListEntry> resultEntrys = loadMostFightCountImages(maxImageCount);

		LOG.debug("added {} graph files", resultEntrys.size());

		// determine max and min win-lose difference
		IntSummaryStatistics summaryStatistics = resultEntrys.stream()//
				.mapToInt(entry -> entry.wins - entry.loses)//
				.summaryStatistics();
		int max = summaryStatistics.getMax();
		int min = summaryStatistics.getMin();

		IntToDoubleFunction normalize = createNormalizeForSigmoidFunction(min, max);

		List<Pair<ImageMLData, BasicMLData>> imageDataList = resultEntrys.stream()//
				.map(entry -> {
					Image image = read(entry.file, downsampleSize * 3);
					ImageMLData imageData = new ImageMLData(image);
					int diff = entry.wins - entry.loses;
					double normalizedDiff = normalize.applyAsDouble(entry.wins - entry.loses);

					BasicMLData targetData = new BasicMLData(new double[] { normalizedDiff });
					LOG.trace("diff {}    normalized:   {}     ", diff, normalizedDiff);

					return new Pair<ImageMLData, BasicMLData>(imageData, targetData);
				})//
				.collect(Collectors.toList());

		Collections.shuffle(imageDataList);

		// Network will not use control data for learning.
		// Use it after training to see how good it works with unknown data.
		int controlDataSize = imageDataList.size() / 5;
		List<Pair<ImageMLData, BasicMLData>> controlDataList = new LinkedList<>();
		while (controlDataList.size() < controlDataSize) {
			controlDataList.add(imageDataList.remove(0));
		}

		LOG.info("imageDataSize: {}   controlDataSize: {}", imageDataList.size(), controlDataList.size());

		ImageMLDataSet trainingData = new ImageMLDataSet(downSampler, findBounds, downSampleHigh, downSampleLow);
		ImageMLDataSet controlData = new ImageMLDataSet(downSampler, findBounds, downSampleHigh, downSampleLow);
		imageDataList.forEach(pair -> trainingData.add(pair.getKey(), pair.getValue()));
		controlDataList.forEach(pair -> controlData.add(pair.getKey(), pair.getValue()));

		// can image sharpness be detected with this strong downsampling?

		// network
		LOG.debug("downsample");
		trainingData.downsample(downsampleSize, downsampleSize);
		controlData.downsample(downsampleSize, downsampleSize);

		int inputSize = trainingData.getInputSize();
		int idealSize = trainingData.getIdealSize();

		LOG.debug("input size: {}", inputSize);

		BasicNetwork network = new BasicNetwork();
		ActivationFunction activationFunction = new ActivationSigmoid();
		BasicLayer inputLayer = new BasicLayer(activationFunction, false, inputSize);

		boolean bias = true;
		network.addLayer(inputLayer);
		IntStream.iterate(inputSize, x -> x - 10)//
				.mapToObj(inSize -> new BasicLayer(activationFunction, bias, inSize))//
				.limit(hiddenLayerCount)//
				.forEach(network::addLayer);

		// TODO what is bias?
		BasicLayer outputLayer = new BasicLayer(activationFunction, true, idealSize);
		network.addLayer(outputLayer);

		network.getStructure().finalizeStructure();
		network.reset();

		printNetworkNeuronCounts(network);

		// training
		LOG.debug("create propagation");

		BasicTraining trainingAlgorithm;

		// ResilientPropagation train = new ResilientPropagation(network, trainingData);
		// train.addStrategy(new ResetStrategy(0.25, 50));
		// Alternatives:

		// Backpropagation backPropagation = new Backpropagation(network, trainingData, 0.5, 0.5);
		// backPropagation.setL1(0.0002);

		ManhattanPropagation manh;
		QuickPropagation qui = new QuickPropagation(network, trainingData, 2.0);
		ScaledConjugateGradient scg;
		StochasticGradientDescent sgd;

		printMemoryStats();

		trainingAlgorithm = qui;

		LOG.debug("add strategy");

		List<Double> errorList = new LinkedList<>();

		Function<MLData, Double> dataToDouble = data -> Double
				.valueOf(EncogUtility.formatNeuralData(data).replace(',', '.'));

		LOG.debug("start training");
		IntStream.rangeClosed(1, iterationCount)//
				.filter(i -> i % 7 == 0)//
				.forEach(iteration -> {

					double[] weights = network.getFlat().getWeights();
					LinkedList<Double> weightsBeforeIteration = new LinkedList<>();
					for (double d : weights) {
						weightsBeforeIteration.add(d);
					}

					trainingAlgorithm.iteration();

					double[] weights2 = network.getFlat().getWeights();
					double sum = 0;
					for (int j = 0; j < weights2.length; j++) {
						// System.err.println("weight change:" + (weightsBeforeIteration.get(j) - weights2[j]));
						sum += Math.abs(weightsBeforeIteration.get(j) - weights2[j]);
					}
					LOG.info("sum of weight changes: {}", sum);

					double error = trainingAlgorithm.getError();
					LOG.info("after iteration: {}  error: {}", iteration, error);
					errorList.add(error);

					// String weightsVerbose = network.dumpWeightsVerbose();
					// System.err.println(weightsVerbose);

					TreeMap<Double, Double> treeMap = new TreeMap<>();

					for (MLDataPair pair : trainingData) {
						/*
						 * FIXME why is output always 1 ? because weights don't change , because the gradients are 0.
						 * Most cases output is 1 or -1. tanh(x) = 1 for x > 2 ... no gradient there ?! => try sigmoid
						 */
						MLData output = network.compute(pair.getInput());
						treeMap.put(dataToDouble.apply(pair.getIdeal()), dataToDouble.apply(output));
						// System.out.println(" Ideal=" + EncogUtility.formatNeuralData(pair.getIdeal()) + " Actual="
						// + EncogUtility.formatNeuralData(output));
					}

					int count = 10;
					Stream<Entry<Double, Double>> first = treeMap.entrySet().stream().limit(count);
					Stream<Entry<Double, Double>> last = treeMap.entrySet().stream().skip(treeMap.size() - count);

					if (iteration == iterationCount) {
						// If every input causes nearly the same output the network is useless.
						Collection<Double> values = treeMap.values();
						double average = values.stream().mapToDouble(d -> d)//
								.summaryStatistics().getAverage();

						long nearToAverageCount = values.stream().filter(val -> Math.abs(average - val) < 0.1)//
								.count();

						if (nearToAverageCount == treeMap.size()) {
							LOG.warn("TOO NARROW: {}", average);
						}

					}

					String idealActualCompare = Stream.concat(first, last)//
							.map(entry -> "ideal:" + entry.getKey() + "   \tactual:" + entry.getValue())//
							.collect(Collectors.joining("\n"));

					LOG.debug("compare data: \n{}", idealActualCompare);
				});

		// testing
		// network.compute(arg0);
		LOG.info("end");

		LOG.info("errorList: \n{}", errorList.stream().map(String::valueOf).collect(Collectors.joining("\n")));

		printNetworkNeuronCounts(network);

		// test network with unknown data
		StringBuilder sb = new StringBuilder();
		controlData.forEach(pair -> {
			MLData input = pair.getInput();
			MLData ideal = pair.getIdeal();
			MLData actual = network.compute(input);
			String line = "ideal:" + dataToDouble.apply(ideal) + "   \tactual:" + dataToDouble.apply(actual) + "\n";
			sb.append(line);
		});

		String controlDataResults = sb.toString();
		LOG.debug("control data results: \n{}", controlDataResults);

		// TODO save the trained network
		File networkSaveFile = new File("network1.eg");
		LOG.debug("network save location: {}", networkSaveFile.getAbsolutePath());
		EncogDirectoryPersistence.saveObject(networkSaveFile, network);

		// EncogDirectoryPersistence.loadObject(file)
		// TODO prepare better data?
		// TODO downsample images before loading?
		// TODO visualize the downsampled images
	}

	private static void printNetworkNeuronCounts(BasicNetwork network) {

		int layerCount = network.getLayerCount();
		for (int i = 0; i < layerCount; i++) {
			System.err.println("layer:" + i + "   nodeCount:" + network.getLayerNeuronCount(i));
		}
	}

	private static Image read(File file, int readSize) {
		try {
			LOG.trace("reading: {}", file.getAbsolutePath());
			javafx.scene.image.Image im;

			FileInputStream fis = new FileInputStream(file);
			javafx.scene.image.Image image = new javafx.scene.image.Image(fis, readSize, readSize, true, true);

			BufferedImage bufferedImage = SwingFXUtils.fromFXImage(image, null);
			// BufferedImage read = ImageIO.read(file);
			// System.err.println(bufferedImage.getWidth() + " - " + bufferedImage.getHeight());
			// System.err.println(read.getWidth() + " - " + read.getHeight());

			// printMemoryStats();

			return bufferedImage;
			// java.lang.OutOfMemoryError: Java heap space after 173 images
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	static void printMemoryStats() {

		int mb = 1024 * 1024;

		// Getting the runtime reference from system
		Runtime runtime = Runtime.getRuntime();

		// Print total available memory
		long total = runtime.totalMemory() / mb;

		// Print Maximum available memory
		long max = runtime.maxMemory() / mb;

		// LOG.debug("used: {} free: {} total: {} max: {} ", used, free, total, max);
		LOG.debug(" {} / {} ", total, max);
	}

	private static IntToDoubleFunction createNormalizeForTanhFunction(int min, int max) {

		// normalize
		// Aim for: the best gets 1.0 and the worst -1.0.
		// scale range diff / ( max - min)
		// (diff) /
		// example: max= 13 , min = -4
		// 13 / ( 17) = 0,76, -4 / 17 = -0,23
		// move range diff - ( max -min)/2 , aim for max-> 8,5 and min-> -8,5 =>
		// move= -4,5
		// example: 13 - 17/2 = 4,5 ; -4 - 17/2 =
		// abs(max) - abs(min) /2
		// 13 - 4 /2 = 9/2 = 4,5
		double translateValue = (Math.abs(max) - Math.abs(min)) / 2.0;
		double maxToMinRange = (max - min);

		LOG.debug("max {}    min   {}     translate  {}     scale    {} ", max, min, translateValue, maxToMinRange);

		IntToDoubleFunction translate = diff -> diff - translateValue;
		DoubleUnaryOperator scale = diff -> diff / maxToMinRange;
		IntToDoubleFunction normalize = diff -> scale.applyAsDouble(translate.applyAsDouble(diff));
		return normalize;
	}

	private static IntToDoubleFunction createNormalizeForSigmoidFunction(int min, int max) {

		/*
		 normalize Aim for: the best gets 1.0 and the worst 0.0.
		 
		 TRANSLATE
		 min -> 0 = min -min
		 max -> max - min
		 diff -> diff - min
		 
		 scale range diff / ( max - min)
		 (diff) /
		 example: max= 13 , min = -4
		 13 / ( 17) = 0,76, -4 / 17 = -0,23
		 move range diff - ( max -min)/2 , aim for max-> 8,5 and min-> -8,5 =>
		 move= -4,5
		 example: 13 - 17/2 = 4,5 ; -4 - 17/2 =
		 abs(max) - abs(min) /2
		 13 - 4 /2 = 9/2 = 4,5
		 */
		double translateValue = min;
		double maxToMinRange = (max - min);

		LOG.debug("max {}    min   {}     translate  {}     scale    {} ", max, min, translateValue, maxToMinRange);

		IntToDoubleFunction translate = diff -> diff - translateValue;
		DoubleUnaryOperator scale = diff -> diff / maxToMinRange;
		IntToDoubleFunction normalize = diff -> scale.applyAsDouble(translate.applyAsDouble(diff));
		return normalize;
	}
}
