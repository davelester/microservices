package eu.interedition.collatex.graph;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import eu.interedition.collatex.Witness;
import org.neo4j.graphdb.Relationship;

import java.util.SortedSet;

/**
 * @author <a href="http://gregor.middell.net/" title="Homepage">Gregor Middell</a>
 */
public class VariantGraphEdge extends GraphEdge<VariantGraph, VariantGraphVertex> {
  private static final String WITNESS_REFERENCE_KEY = "witnessReferences";

  public VariantGraphEdge(VariantGraph graph, Relationship relationship) {
    super(graph, relationship);
  }

  public VariantGraphEdge(VariantGraph graph, VariantGraphVertex from, VariantGraphVertex to, SortedSet<Witness> witnesses) {
    this(graph, from.getNode().createRelationshipTo(to.getNode(), GraphRelationshipType.PATH));
    setWitnesses(witnesses);
  }

  public boolean traversableWith(SortedSet<Witness> witnesses) {
    return (witnesses == null || witnesses.isEmpty() || Iterables.any(getWitnesses(), Predicates.in(witnesses)));
  }

  public VariantGraphEdge add(SortedSet<Witness> witnesses) {
    final SortedSet<Witness> registered = getWitnesses();
    registered.addAll(witnesses);
    setWitnesses(registered);
    return this;
  }

  public SortedSet<Witness> getWitnesses() {
    return Sets.newTreeSet(graph.getWitnessResolver().resolve(getWitnessReferences()));
  }

  public void setWitnesses(SortedSet<Witness> witnesses) {
    setWitnessReferences(graph.getWitnessResolver().resolve(witnesses));
  }

  public int[] getWitnessReferences() {
    return (int[]) relationship.getProperty(WITNESS_REFERENCE_KEY);
  }

  public void setWitnessReferences(int... references) {
    relationship.setProperty(WITNESS_REFERENCE_KEY, references);
  }


  public static Function<Relationship, VariantGraphEdge> createWrapper(final VariantGraph in) {
    return new Function<Relationship, VariantGraphEdge>() {
      @Override
      public VariantGraphEdge apply(Relationship input) {
        return new VariantGraphEdge(in, input);
      }
    };
  }

  public static Predicate<VariantGraphEdge> createTraversableFilter(final SortedSet<Witness> witnesses) {
    return new Predicate<VariantGraphEdge>() {
      @Override
      public boolean apply(VariantGraphEdge input) {
        return input.traversableWith(witnesses);
      }
    };
  }

  public static final Function<VariantGraphEdge, String> TO_CONTENTS = new Function<VariantGraphEdge, String>() {
    @Override
    public String apply(VariantGraphEdge input) {
      return Joiner.on(", ").join(input.getWitnesses());
    }
  };
}
