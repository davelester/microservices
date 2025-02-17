package eu.interedition.collatex.graph;


import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.AbstractIterator;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import eu.interedition.collatex.Witness;
import eu.interedition.collatex.Token;
import eu.interedition.collatex.input.SimpleToken;
import eu.interedition.collatex.matching.Matches;
import org.neo4j.graphalgo.WeightedPath;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.traversal.Evaluation;
import org.neo4j.graphdb.traversal.Evaluator;
import org.neo4j.graphdb.traversal.TraversalDescription;
import org.neo4j.kernel.Traversal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;

import static com.google.common.collect.Iterables.transform;
import static eu.interedition.collatex.graph.EditOperation.GAP;
import static eu.interedition.collatex.graph.EditOperation.NO_GAP;
import static eu.interedition.collatex.graph.GraphRelationshipType.PATH;
import static org.neo4j.graphalgo.GraphAlgoFactory.dijkstra;
import static org.neo4j.graphdb.Direction.OUTGOING;
import static org.neo4j.kernel.StandardExpander.DEFAULT;
import static org.neo4j.kernel.Uniqueness.RELATIONSHIP_GLOBAL;

/**
 * This class is the container class for the Edit Graph.
 * <p/>
 * This is a mutable class that is constructed by an external class, since the construction process is an elaborate one. This class is implemented in a defensive style. We use a weighted DAG to make alignment decisions.
 */
@SuppressWarnings("serial")
public class EditGraph extends Graph<EditGraphVertex, EditGraphEdge> {
  private static final Logger LOG = LoggerFactory.getLogger(EditGraph.class);
  private final Function<Node, VariantGraphVertex> variantGraphVertexWrapper;

  public EditGraph(GraphDatabaseService database, Resolver<Witness> witnessResolver, Resolver<Token> tokenResolver, Function<Node, VariantGraphVertex> variantGraphVertexWrapper) {
    super(database, witnessResolver, tokenResolver);
    this.variantGraphVertexWrapper = variantGraphVertexWrapper;
  }

  public Function<Node, VariantGraphVertex> getVariantGraphVertexWrapper() {
    return variantGraphVertexWrapper;
  }

  public Iterable<EditGraphVertex> vertices() {
    return transform(createTraversalDescription().traverse(start.getNode()).nodes(), vertexWrapper);
  }

  public Iterable<EditGraphEdge> edges() {
    return transform(createTraversalDescription().uniqueness(RELATIONSHIP_GLOBAL).traverse(start.getNode()).relationships(), edgeWrapper);
  }

  protected TraversalDescription createTraversalDescription() {
    return Traversal.description().breadthFirst().relationships(PATH, OUTGOING);
  }

  public EditGraph build(VariantGraph base, SortedSet<Token> witness, Comparator<Token> comparator) {
    base.rank();
    Set<EditGraphVertex> prevVertexSet = Sets.newLinkedHashSet();
    prevVertexSet.add(start);
    // build the decision graph from the matches and the variant graph
    Matches m = Matches.between(base.vertices(), witness, comparator);
    Set<String> ambiguousNormalized = getAmbiguousNormalizedContent(m);
    ListMultimap<Token, VariantGraphVertex> matches = m.getAll();
    // add for vertices for witness tokens that have a matching base token
    int witnessIndex = 0;
    for (Token witnessToken : witness) {
      List<VariantGraphVertex> baseTokens = matches.get(witnessToken);
      if (!baseTokens.isEmpty()) {
        final Set<EditGraphVertex> vertexSet = Sets.newLinkedHashSet();
        for (VariantGraphVertex baseToken : baseTokens) {
          EditGraphVertex editGraphVertex = new EditGraphVertex(this, baseToken, witnessToken, witnessIndex);
          vertexSet.add(editGraphVertex);

          // TODO: you don't want to always draw an edge
          // TODO: in the case of ngrams in witness and superbase
          // TODO: less edges are needed
          for (EditGraphVertex prevVertex : prevVertexSet) {
            connect(prevVertex, editGraphVertex, base.isNear(prevVertex.getBase(), baseToken) ? NO_GAP : GAP);
          }
        }
        prevVertexSet = vertexSet;
      }
      witnessIndex++;
    }

    end.setWitnessIndex(witnessIndex);

    // add edges to end vertex
    for (EditGraphVertex lastVertex : prevVertexSet) {
      connect(lastVertex, end, base.isNear(lastVertex.getBase(), end.getBase()) ? NO_GAP : GAP);
    }

    score();

    //addSkipVertices(ambiguousNormalized);

    int pathId = 0;
    for (WeightedPath path : dijkstra(DEFAULT.add(PATH, OUTGOING), new EditGraphEdgeCostEvualator()).findAllPaths(start.getNode(), end.getNode())) {
      for (EditGraphEdge edge : transform(path.relationships(), edgeWrapper)) {
        edge.addShortestPathId(pathId);
      }
      pathId++;
    }

    return this;
  }

//  protected void score() {
//    for (EditGraphEdge e : edges()) {
//      int score = (e.to().getWitnessIndex() - e.from().getWitnessIndex()) - 1;
//      if (e.getEditOperation() == GAP) {
//        score += 1;
//      }
//
//      final Iterable<EditGraphEdge> prevEdges = e.from().incoming();
//      boolean sequence = true;
//      for (EditGraphEdge prev : prevEdges) {
//        if (prev.getEditOperation() != e.getEditOperation()) {
//          sequence = false;
//          break;
//        }
//      }
//      if (!Iterables.isEmpty(prevEdges) && sequence) {
//        score -= 1;
//      }
//
//      e.setScore(score);
//    }
//  }

  protected void score() {
    Iterable<EditGraphEdge> edgesInTopologicalOrder = edgesInTopologicalOrder();
    for (EditGraphEdge edge : edgesInTopologicalOrder) {
      Score score = determineScore(edge);
      LOG.debug("Scoring edge {} {}", edge, score);
      edge.setScore(score);
    }
  }
  
  public Iterable<Iterable<EditGraphEdge>> shortestPaths() {
    int maxId = -1;
    for (EditGraphEdge e : start.outgoing()) {
      final SortedSet<Integer> shortestPathIds = e.getShortestPathIds();
      if (!shortestPathIds.isEmpty()) {
        maxId = Math.max(maxId, shortestPathIds.last());
      }
    }
    
    final int lastPathId = maxId;
    return new Iterable<Iterable<EditGraphEdge>>() {
      @Override
      public Iterator<Iterable<EditGraphEdge>> iterator() {
        return new AbstractIterator<Iterable<EditGraphEdge>>() {
          private int pathId = 0;
          @Override
          protected Iterable<EditGraphEdge> computeNext() {
            if (pathId > lastPathId) {
              return endOfData();
            }
            
            return shortestPath(pathId++);
          }
        };
      }
    };
  }
  
  public Iterable<EditGraphEdge> shortestPath(final int id) {
    return transform(createTraversalDescription().evaluator(new Evaluator() {
      @Override
      public Evaluation evaluate(Path path) {
        final Relationship rel = path.lastRelationship();
        if (rel == null) {
          return Evaluation.INCLUDE_AND_CONTINUE;
        }

        final SortedSet<Integer> pathIds = edgeWrapper.apply(rel).getShortestPathIds();
        return (pathIds.contains(id) ? Evaluation.INCLUDE_AND_CONTINUE : Evaluation.EXCLUDE_AND_PRUNE);
      }
    }).traverse(start.getNode()).relationships(), edgeWrapper);
  }

  public Map<Token, VariantGraphVertex> linkedTokens() {
    final BiMap<Token, VariantGraphVertex> linkedTokens = HashBiMap.create();
    for (Iterable<EditGraphEdge> shortestPath : shortestPaths()) {
      for (EditGraphEdge e : shortestPath) {
        final EditGraphVertex vertex = e.from();
        if (vertex.equals(start)) {
          continue;
        }
        final VariantGraphVertex baseToken = vertex.getBase();
        if (linkedTokens.containsValue(baseToken)) {
          LOG.warn("Duplicate match for base token {}", baseToken);
          continue;
        }
        linkedTokens.put(vertex.getWitness(), baseToken);
      }
      break;
    }
    return linkedTokens;
  }

  public EditGraphEdge connect(EditGraphVertex from, EditGraphVertex to, EditOperation operation) {
    Preconditions.checkArgument(!from.equals(to));

    for (EditGraphEdge e : from.outgoing()) {
      if (to.equals(e.to())) {
        throw new IllegalStateException(String.format("%s and %s already connected", from, to));
      }
    }
    return new EditGraphEdge(this, from, to, operation);
  }

  private Iterable<EditGraphEdge> edgesInTopologicalOrder() {
    List<Iterable<EditGraphEdge>> sortedEdges = Lists.newArrayList(); 
    Iterable<EditGraphVertex> vertices = vertices();
    for (EditGraphVertex v : vertices) {
      sortedEdges.add(v.outgoing());
    }
    return Iterables.concat(sortedEdges);
  }

  private Score determineScore(EditGraphEdge edge) {
    EditGraphVertex from = edge.from();
    //NOTE: not so nice way of determining whether edges originated from start vertex
    //NOTE: it might be better to use equals graph.getStart()?
    if (Iterables.isEmpty(from.incoming())) {
      Score score = new Score();
      if (edge.getEditOperation()==EditOperation.NO_GAP) {
        score.addMatch();
      } else {
        score.àddGap();
      }
      return score;
    }
    // walk over all the incoming edges and determine the next score
    Map<EditGraphEdge, Score> scores = Maps.newHashMap();
    for (EditGraphEdge incomingEdge : from.incoming()) {
      Score newScore = determineScore(incomingEdge, edge);
      scores.put(incomingEdge, newScore);
    }
    EditGraphEdge minimalScoringIncomingEdge = findMinimalScoringIncomingEdge(scores);
    return scores.get(minimalScoringIncomingEdge);
  }

  
//      EditGraphEdge prevEdge = findMinimalScoringIncomingEdge(from);
//    //check for a jump backwards..
//    //if so, do not deduce the score, cause it would brake the sequence
//    int distance = edge.to().getBase().getRank() - edge.from().getBase().getRank();
//    LOG.debug("distance: {}", distance);
//    //TODO: this does not always work.. remember: ommissions cause jump in rank, not only real transpositions!
//    if (distance<0||distance>1) {
//      // we have detected a jump backwards.. we are not in sequence..
//      return prevEdge.getScore();
//    }
  
//      EditGraphEdge prevEdge = findMinimalScoringIncomingEdge(from);
//    //check for a jump backwards..
//    //if so, do not deduce the score, cause it would brake the sequence
//    int distance = edge.to().getBase().getRank() - edge.from().getBase().getRank();
//    LOG.debug("distance: {}", distance);
//    //TODO: this does not always work.. remember: ommissions cause jump in rank, not only real transpositions!
//    if (distance<0||distance>1) {
//      // we have detected a jump backwards.. we are not in sequence..
//      return prevEdge.getScore();
//    }
    
    
    
    
 
  // there are 4 possibilities here
  // no_gap/no_gap --> ideal
  // gap/gap --> less ideal, but ok
  // gap/no_gap --> close gap scoring, start new etc
  // no_gap/gap --> close match seq scoring, start new etc
  private Score determineScore(EditGraphEdge incomingEdge, EditGraphEdge outgoingEdge) {
    Score newScore = incomingEdge.getScore().copy();
    // if the editoperation of the incomingEdge is the same as the outgoingEdge to score
    // we are in sequence and we add match/gap to score
    // otherwise we first transition state (this should cause a penalty) and then we add match/gap to score
    if (incomingEdge.getEditOperation() != outgoingEdge.getEditOperation()) {
      newScore.transitionState();
    }
    if (outgoingEdge.getEditOperation()==EditOperation.NO_GAP) {
      newScore.addMatch();
    } else {
      newScore.àddGap();
    }
    return newScore;
  }
  
  // NOTE: warning: there can be multiple incoming edges with the same minimum score..
  private EditGraphEdge findMinimalScoringIncomingEdge(Map<EditGraphEdge, Score> scores) {
    EditGraphEdge minimumEdge = scores.keySet().iterator().next();
    for (EditGraphEdge edge: scores.keySet()) {
      if (edge.getScore().getTempScore() < minimumEdge.getScore().getTempScore()) {
        minimumEdge = edge;
      }
    }
    return minimumEdge;
  }

  private void addSkipVertices(Set<String> ambiguousNormalized) {
    for (EditGraphVertex vertex : vertices()) {
      Token witnessToken = vertex.getWitness();
      if (witnessToken != null) {
        String normalized = ((SimpleToken) witnessToken).getNormalized();
        if (ambiguousNormalized.contains(normalized)) {
          Set<EditGraphEdge> incomingEdges = Sets.newHashSet(vertex.incoming());
          Set<EditGraphEdge> outgoingEdges = Sets.newHashSet(vertex.outgoing());
//          for (EditGraphEdge incomingEdge : incomingEdges) {
//            for (EditGraphEdge outgoingEdge : outgoingEdges) {
//              connect(incomingEdge.from(), outgoingEdge.to(), EditOperation.GAP, 3);
//            }
//          }
          final EditGraphVertex skipVertex = new EditGraphVertex(null, null, null, 0);
          for (EditGraphEdge incomingEdge : incomingEdges) {
            //connect(incomingEdge.from(), skipVertex, GAP).setScore(3);
          }
          for (EditGraphEdge outgoingEdge : outgoingEdges) {
            connect(skipVertex, outgoingEdge.to(), NO_GAP);
          }
        }
      }
    }
  }

  private Set<String> getAmbiguousNormalizedContent(Matches m) {
    Set<Token> ambiguousMatches = m.getAmbiguous();
    Set<String> ambiguousNormalized = Sets.newHashSet();
    for (Token token : ambiguousMatches) {
      ambiguousNormalized.add(((SimpleToken) token).getNormalized());
    }
    return ambiguousNormalized;
  }
}
