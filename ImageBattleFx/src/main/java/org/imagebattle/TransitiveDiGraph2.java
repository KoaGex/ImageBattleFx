package org.imagebattle;

import java.io.File;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.function.BiConsumer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleDirectedGraph;

import javafx.util.Pair;

public class TransitiveDiGraph2 extends SimpleDirectedGraph<File, DefaultEdge> {
    private static Logger log = LogManager.getLogger();

    /**
     * Constructor
     * 
     * @param pNodes
     */
    public TransitiveDiGraph2(List<File> pNodes) {
	super(DefaultEdge.class);
	pNodes.forEach(this::addVertex);
    }

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    @Override
    public DefaultEdge addEdge(File sourceVertex, File targetVertex) {
	// DefaultEdge result = super.addEdge(sourceVertex, targetVertex);

	DefaultEdge result = null;

	log.trace(sourceVertex.getName() + " won against " + targetVertex.getName());

	BiConsumer<File, File> addEdge = (from, to) -> {
	    boolean edgeExists = super.containsEdge(from, to) || super.containsEdge(to, from);
	    if (edgeExists) {
		log.trace("edge already set:" + from.getName() + " -> " + to.getName());
	    } else {
		log.trace("add edge " + from.getName() + " -> " + to.getName());
		// use super. without it would quickly result in an infinite
		// recursive loop
		DefaultEdge result2 = super.addEdge(from, to); // TODO use the
							       // return value
							       // ?!
		// result = (result == null) ? result2 : result; TODO after
		// adding use getEdge ?
	    }
	};

	int edgeCountOld = super.edgeSet().size();

	// ensure transitivity
	Queue<Pair<File, File>> queue = new LinkedList<Pair<File, File>>();

	queue.add(new Pair<File, File>(sourceVertex, targetVertex));
	while (!queue.isEmpty()) {

	    Pair<File, File> current = queue.poll();
	    File b = current.getKey();
	    File c = current.getValue();

	    addEdge.accept(b, c);

	    // b -> c -> d
	    super.outgoingEdgesOf(c).stream()//
		    .map(super::getEdgeTarget) //
		    .filter(d -> !super.containsEdge(b, d) && !super.containsEdge(d, b)) // TODO
											 // double
											 // check
											 // needed?
		    .map(d -> new Pair<File, File>(b, d))//
		    .peek(log::trace) //
		    .forEach(queue::add);

	    // a -> b -> c
	    super.incomingEdgesOf(b).stream()//
		    .map(super::getEdgeSource) //
		    .filter(a -> !super.containsEdge(c, a) && !super.containsEdge(a, c)) // TODO
											 // double
											 // check
											 // needed?
		    .map(a -> new Pair<File, File>(a, c))//
		    .peek(log::trace) //
		    .forEach(queue::add);
	}

	int edgeCountNew = super.edgeSet().size();
	int edgesAdded = edgeCountNew - edgeCountOld;
	int nodeCount = super.vertexSet().size();
	int ofMaximal = nodeCount * (nodeCount - 1) / 2;
	double percent = Double.valueOf(edgeCountNew) / Double.valueOf(ofMaximal);
	log.debug("added {} and now have {} edges of {} possible. In Percent: {}", edgesAdded, edgeCountNew, ofMaximal,
		percent);

	return result;
    }

    int getMaxEdgeCount() {
	Set<File> vertexSet = this.vertexSet();
	int nodeCount = vertexSet.size();
	int maxEdgeCount = nodeCount * (nodeCount - 1) / 2;
	return maxEdgeCount;
    }

    int getCurrentEdgeCount() {
	return this.edgeSet().size();
    }

    public Integer getWinLoseDifference(File file) {
	return outDegreeOf(file) - inDegreeOf(file);
    }

    public boolean containsAnyEdge(File v1, File v2) {
	return containsEdge(v1, v2) || containsEdge(v2, v1);
    }
}