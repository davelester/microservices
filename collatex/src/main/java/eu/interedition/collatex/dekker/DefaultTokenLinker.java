package eu.interedition.collatex.dekker;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import eu.interedition.collatex.Token;
import eu.interedition.collatex.graph.VariantGraph;
import eu.interedition.collatex.graph.VariantGraphVertex;
import eu.interedition.collatex.input.SimpleToken;
import eu.interedition.collatex.matching.Matches;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;

import static com.google.common.collect.Lists.reverse;

public class DefaultTokenLinker implements TokenLinker {
  private static final Logger LOG = LoggerFactory.getLogger(DefaultTokenLinker.class);

  private Matches matches;
  private List<List<Token>> leftExpandingPhrases;
  private List<List<Token>> rightExpandingPhrases;
  private List<Integer> ranks;
  private List<List<Match>> phraseMatches;
  private Map<Token, VariantGraphVertex> tokenLinks;

  @Override
  public Map<Token, VariantGraphVertex> link(VariantGraph base, SortedSet<Token> witness, Comparator<Token> comparator) {
    Preconditions.checkArgument(!witness.isEmpty(), "Empty witness");

    base.rank();

    if (LOG.isTraceEnabled()) {
      LOG.trace("Matching tokens of {} and {}", base, witness.first().getWitness());
    }
    matches = Matches.between(base.vertices(), witness, comparator);

    LOG.trace("Finding minimal unique token sequences");
    final List<Token> witnessTokens = Lists.newArrayList(witness);
    final int witnessTokenCount = witnessTokens.size();

    leftExpandingPhrases = Lists.newArrayListWithExpectedSize(matches.getAmbiguous().size());
    rightExpandingPhrases = Lists.newArrayListWithExpectedSize(matches.getAmbiguous().size());

    for (int tc = 0; tc < witnessTokenCount; tc++) {
      // for each ambiguous token
      if (matches.getAmbiguous().contains(witnessTokens.get(tc))) {
        // find a minimal unique phrase by walking to the left
        rightExpandingPhrases.add(reverse(findMinimalUniquePrefix(reverse(witnessTokens.subList(0, tc + 1)), SimpleToken.START)));
        // find a minimal unique phrase by walking to the right
        leftExpandingPhrases.add(findMinimalUniquePrefix(witnessTokens.subList(tc, witnessTokenCount), SimpleToken.END));
      }
    }

    LOG.trace("Find matches in the base");
    List<VariantGraphVertex> baseMatches = Lists.newArrayList(Iterables.filter(base.vertices(), Predicates.in(matches.getAll().values())));

    LOG.trace("Find all the ranks of the vertices of the VG that are matched against");
    // NOTE: Not all ranks are actually in use (because of ommissions)
    // gather matched ranks into a set ordered by their natural order
    // Turn it into a List so that distance between matched ranks can be called
    // Note that omitted vertices are not in the list, so they don't cause an extra phrasematch
    ranks = Lists.newArrayList(Sets.newTreeSet(Iterables.transform(baseMatches, VariantGraphVertex.TO_RANK)));
    if (LOG.isTraceEnabled()) {
      LOG.trace("Base: {}", baseMatches);
      LOG.trace("Ranks: {}", ranks);
    }

    // try and find matches in the base for each sequence in the witness
    phraseMatches = Lists.newArrayList();
    for (List<Token> phrase : rightExpandingPhrases) {
      final List<VariantGraphVertex> matchingPhrase = matchPhrase(phrase, 1);
      if (!matchingPhrase.isEmpty()) {        
        phraseMatches.add(Match.createPhraseMatch(matchingPhrase, phrase));
      }
    }
    for (List<Token> phrase : leftExpandingPhrases) {
      final List<VariantGraphVertex> matchingPhrase = reverse(matchPhrase(reverse(phrase), -1));
      if (!matchingPhrase.isEmpty()) {
        phraseMatches.add(Match.createPhraseMatch(matchingPhrase, phrase));
      }
    }

    // run the old filter method
    filterAlternativePhraseMatches(base, phraseMatches);

    // do the matching
    final ListMultimap<Token, VariantGraphVertex> allMatches = matches.getAll();

    tokenLinks = Maps.newLinkedHashMap();

    for (Token unique : matches.getUnique()) {
      // put unique matches in the result
      tokenLinks.put(unique, Iterables.getFirst(allMatches.get(unique), null));
    }
    // add matched sequences to the result
    for (List<Match> phraseMatch : phraseMatches) {
      for (Match match : phraseMatch) {
        if (SimpleToken.START.equals(match.token) || SimpleToken.END.equals(match.token)) {
          // skip start and end tokens
          continue;
        }
        tokenLinks.put(match.token, match.vertex);
      }
    }
    return tokenLinks;
  }

  public Matches getMatches() {
    return matches;
  }

  public List<List<Token>> getLeftExpandingPhrases() {
    return leftExpandingPhrases;
  }

  public List<List<Token>> getRightExpandingPhrases() {
    return rightExpandingPhrases;
  }

  public List<List<Match>> getPhraseMatches() {
    return phraseMatches;
  }

  public Map<Token, VariantGraphVertex> getTokenLinks() {
    return tokenLinks;
  }

  public List<Token> findMinimalUniquePrefix(Iterable<Token> phrase, Token stopMarker) {
    final List<Token> result = Lists.newArrayList();

    for (Token token : phrase) {
      if (!matches.getUnmatched().contains(token)) {
        result.add(token);
        if (!matches.getAmbiguous().contains(token)) {
          return result;
        }
      }
    }

    result.add(stopMarker);
    return result;
  }

  private void filterAlternativePhraseMatches(VariantGraph graph, List<List<Match>> phraseMatches) {
    final Map<Token, VariantGraphVertex> previousMatches = Maps.newHashMap();
    final Map<VariantGraphVertex, Token> invertedPreviousMatches = Maps.newHashMap();

    for (Iterator<List<Match>> phraseMatchIt = phraseMatches.iterator(); phraseMatchIt.hasNext(); ) {
      final List<Match> phraseMatch = phraseMatchIt.next();
      boolean foundAlternative = false;

      for (Match match : Iterables.filter(phraseMatch, Match.createNoBoundaryMatchPredicate(graph))) {
        final VariantGraphVertex matchingVertex = previousMatches.get(match.token);
        if (matchingVertex != null && !matchingVertex.equals(match.vertex)) {
          foundAlternative = true;
        } else {
          previousMatches.put(match.token, match.vertex);
        }

        final Token matchingToken = invertedPreviousMatches.get(match.vertex);
        if (matchingToken != null && !matchingToken.equals(match.token)) {
          foundAlternative = true;
        } else {
          invertedPreviousMatches.put(match.vertex, match.token);
        }
      }

      if (foundAlternative) {
        phraseMatchIt.remove();
      }
    }
  }

  private List<VariantGraphVertex> matchPhrase(List<Token> phrase, int expectedDirection) {
    final List<VariantGraphVertex> matchedPhrase = Lists.newArrayList();
    if (LOG.isTraceEnabled()) {
      LOG.trace("Trying to find phrase: {}", phrase);
    }
       
    VariantGraphVertex lastMatch = null;
    int lastMatchIndex = 0;
    for (Token token : phrase) {
      if (lastMatch == null) {
        lastMatch = Iterables.get(matches.getAll().get(token), 0);
        lastMatchIndex = ranks.indexOf(lastMatch.getRank());
        matchedPhrase.add(lastMatch);
        continue;
      }
      boolean tokenMatched = false;
      for (VariantGraphVertex match : matches.getAll().get(token)) {
        final int matchIndex = ranks.indexOf(match.getRank());
        int direction = matchIndex - lastMatchIndex;
        if (direction == expectedDirection) {
          lastMatch = match;
          lastMatchIndex = matchIndex;
          matchedPhrase.add(match);
          tokenMatched = true;
          break;
        }
      }
      if (!tokenMatched) {
        return Collections.emptyList();
      }
    }

    if (LOG.isTraceEnabled()) {
      LOG.trace("Found phrase: {}", phrase);
    }
    return matchedPhrase;
  }
}
