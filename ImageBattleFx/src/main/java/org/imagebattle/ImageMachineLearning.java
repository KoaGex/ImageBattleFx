package org.imagebattle;

import java.awt.Image;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.IntSummaryStatistics;
import java.util.Set;
import java.util.function.DoubleUnaryOperator;
import java.util.function.IntToDoubleFunction;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.encog.ml.data.basic.BasicMLData;
import org.encog.ml.train.strategy.ResetStrategy;
import org.encog.neural.networks.BasicNetwork;
import org.encog.neural.networks.training.propagation.resilient.ResilientPropagation;
import org.encog.platformspecific.j2se.data.image.ImageMLData;
import org.encog.platformspecific.j2se.data.image.ImageMLDataSet;
import org.encog.util.downsample.RGBDownsample;
import org.encog.util.simple.EncogUtility;

/**
 * For testing encog.
 * 
 * VM Arguments: -Xmx8000M
 * 
 * I know 8 gb is a little bit crazy, need to work on that.
 * 
 * @author KoaGex
 *
 */
public class ImageMachineLearning {
    private static final Logger LOG = LogManager.getLogger();

    private static final long MAX_IMAGE_COUNT = 25;

    /**
     * 
     * @param args
     */
    public static void main(String[] args) {
	LOG.info("start");
	RGBDownsample downSampler = new RGBDownsample();
	boolean findBounds = false;
	ImageMLDataSet training = new ImageMLDataSet(downSampler, findBounds, 1, -1);

	// TODO idea 1 : binary classifier ( ignore? )
	// data : ignored
	BasicMLData ignoreYes = new BasicMLData(new double[] { -1.0 });

	CentralStorage.readIgnoreFile().stream()//
		.limit(MAX_IMAGE_COUNT) // to avoid heap space error
		.map(ImageMachineLearning::read)//
		.map(ImageMLData::new)//
		.forEach(imageData -> training.add(imageData, ignoreYes));

	long ignoredCount = training.getRecordCount();
	LOG.debug("added {} ignored files", ignoredCount);

	// data: graph
	TransitiveDiGraph2 imageGraph = CentralStorage.readGraph(new File("D:\\"),
		ImageBattleApplication.imagePredicate, true);

	Set<ResultListEntry> resultEntrys = imageGraph.vertexSet().stream()//
		.limit(MAX_IMAGE_COUNT) // to avoid heap space error
		.map(imageGraph::fileToResultEntry)//
		.collect(Collectors.toSet());

	LOG.debug("added {} graph files", resultEntrys.size());

	// determine max and min win-lose difference
	IntSummaryStatistics summaryStatistics = resultEntrys.stream()//
		.mapToInt(entry -> entry.wins - entry.loses)//
		.summaryStatistics();
	int max = summaryStatistics.getMax();
	int min = summaryStatistics.getMin();

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

	resultEntrys.stream()//
		.forEach(entry -> {
		    Image image = read(entry.file);
		    ImageMLData imageData = new ImageMLData(image);
		    int diff = entry.wins - entry.loses;
		    double normalizedDiff = normalize.applyAsDouble(entry.wins - entry.loses);
		    BasicMLData targetData = new BasicMLData(new double[] { normalizedDiff });
		    LOG.debug("diff {}    normalized   {}     ", diff, normalizedDiff);

		    training.add(imageData, targetData);
		});
		// .map(ImageMachineLearning::read)//
		// .map(ImageMLData::new)//
		// .forEach(imageData -> training.add(imageData, ignoreYes));

	// can image sharpness be detected with this strong downsampling?

	// network
	LOG.debug("downsample");
	training.downsample(50, 50); // sample down to 50x50 pixels
	int inputSize = training.getInputSize();
	int idealSize = training.getIdealSize();

	LOG.debug("input size: {}", inputSize);

	BasicNetwork network = EncogUtility.simpleFeedForward(inputSize, inputSize / 2, inputSize / 4, idealSize, true);

	// training
	LOG.debug("create propagation");
	ResilientPropagation train = new ResilientPropagation(network, training);

	LOG.debug("add strategy");
	train.addStrategy(new ResetStrategy(0.25, 50));

	LOG.debug("start training");
	EncogUtility.trainConsole(network, training, 10);
	// 3 Minutes training with 35 images each
	// Iter #1 Error:199,051991% elapsed= 00:01:23
	// Iter #2 Error:91,217617% elapsed= 00:02:46
	// Iter #3 Error:91,570491% elapsed= 00:04:09

	// 10 minutes with 25 each
	// Iter #1 Error:170,635901% elapsed= 00:00:59
	// Iter #2 Error:89,118240% elapsed= 00:01:59
	// Iter #3 Error:140,653510% elapsed= 00:02:58
	// Iter #4 Error:154,943966% elapsed= 00:03:57
	// Iter #5 Error:154,943966% elapsed= 00:04:57
	// Iter #6 Error:154,943966% elapsed= 00:05:56
	// Iter #7 Error:154,943966% elapsed= 00:06:55
	// Iter #8 Error:154,943966% elapsed= 00:07:55
	// Iter #9 Error:154,943966% elapsed= 00:08:55
	// Iter #10 Error:154,943966% elapsed= 00:09:55
	// => seems like just throwing in training time does not help

	// testing
	// network.compute(arg0);
	LOG.info("end");

	// TODO save the trained network
	// EncogDirectoryPersistence.saveObject(filename, obj);
	// EncogDirectoryPersistence.loadObject(file)
	// TODO prepare better data?
	// TODO test with training data and new data
	// TODO downsample images before loading?
    }

    private static Image read(File file) {
	try {
	    LOG.debug("reading: {}", file.getAbsolutePath());
	    return ImageIO.read(file);
	    // java.lang.OutOfMemoryError: Java heap space after 173 images
	} catch (IOException e) {
	    throw new UncheckedIOException(e);
	}
    }

}
