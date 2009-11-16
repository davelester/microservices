package eu.interedition.collatex.match;

import java.util.Set;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.sd_editions.collatex.match.Subsegment;

import eu.interedition.collatex.alignment.Match;
import eu.interedition.collatex.alignment.UnfixedAlignment;
import eu.interedition.collatex.input.Phrase;
import eu.interedition.collatex.input.Segment;
import eu.interedition.collatex.input.WitnessSegmentPhrases;
import eu.interedition.collatex.input.Word;
import eu.interedition.collatex.match.worddistance.NormalizedLevenshtein;
import eu.interedition.collatex.match.worddistance.WordDistance;

////////////////////////////////////////
// 1. Tokenization
// 2. Regularization
// 3. Segmentation
// 4. Matching
// This class does the Matching!
//
public class Matcher {
  //TODO: TESTING: add replacement tests!
  //TODO: TESTING: add check for alternative matches!

  // NOTE: maybe rename UnfixedAlignment back to Matches?
  public static UnfixedAlignment<Word> match(final Segment a, final Segment b) {
    final Set<Match<Word>> allMatches = findMatches(a, b, new NormalizedLevenshtein());

    final UnfixedAlignment<Word> unfixedAlignment = separateAllMatchesIntoFixedAndUnfixedMatches(allMatches);
    return unfixedAlignment;
  }

  // NOTE: this code is specific for Phrase matching.
  // TODO: make this code return an UnfixedAlignment object
  // TODO: make this code similar to match(Segment, Segment)
  public static UnfixedAlignment<Phrase> match(final WitnessSegmentPhrases pa, final WitnessSegmentPhrases pb) {
    if (pa.size() > pb.size()) {
      final Set<Match<Phrase>> matches_wrong = Matcher.match2(pb, pa);
      final Set<Match<Phrase>> matches_right = Sets.newLinkedHashSet();
      for (final Match<Phrase> match : matches_wrong) {
        matches_right.add(new Match<Phrase>(match.getWitnessWord(), match.getBaseWord()));
      }
      final Set<Match<Phrase>> unfixedMatches = Sets.newLinkedHashSet();
      final UnfixedAlignment<Phrase> result = new UnfixedAlignment<Phrase>(matches_right, unfixedMatches);
      return result;
    }
    final Set<Match<Phrase>> fixedMatches = Matcher.match2(pa, pb);
    final Set<Match<Phrase>> unfixedMatches = Sets.newLinkedHashSet();
    final UnfixedAlignment<Phrase> result = new UnfixedAlignment<Phrase>(fixedMatches, unfixedMatches);
    return result;
  }

  // NOTE: this code is specific for Segments/Words!
  static Set<Match<Word>> findMatches(final Segment base, final Segment witness, final WordDistance distanceMeasure) {
    final Set<Match<Word>> matchSet = Sets.newLinkedHashSet();
    for (final Word baseWord : base.getWords()) {
      for (final Word witnessWord : witness.getWords()) {
        if (baseWord.normalized.equals(witnessWord.normalized)) {
          matchSet.add(new Match<Word>(baseWord, witnessWord));
        } else {
          final float editDistance = distanceMeasure.distance(baseWord.normalized, witnessWord.normalized);
          if (editDistance < 0.5) matchSet.add(new Match<Word>(baseWord, witnessWord, editDistance));
        }
      }
    }
    return matchSet;
  }

  private static UnfixedAlignment<Word> separateAllMatchesIntoFixedAndUnfixedMatches(final Set<Match<Word>> allMatches) {
    // Note: this code is not the simplest thing that 
    // could possibly work!
    final Set<Match<Word>> exactMatches = Sets.newLinkedHashSet();
    for (final Match<Word> match : allMatches) {
      final Iterable<Match<Word>> alternatives = findAlternatives(allMatches, match);
      if (!alternatives.iterator().hasNext()) {
        exactMatches.add(match);
      }
    }

    final Set<Match<Word>> unfixedMatches = Sets.newLinkedHashSet(allMatches);
    unfixedMatches.removeAll(exactMatches);

    final UnfixedAlignment<Word> unfixedAlignment = new UnfixedAlignment<Word>(exactMatches, unfixedMatches);
    return unfixedAlignment;
  }

  static Iterable<Match<Word>> findAlternatives(final Iterable<Match<Word>> pmatches, final Match<Word> pmatch) {
    final Predicate<Match<Word>> unfixedAlternativeToGivenPMatch = new Predicate<Match<Word>>() {
      public boolean apply(final Match<Word> pm) {
        return pm != pmatch && (pm.getBaseWord().equals(pmatch.getBaseWord()) || pm.getWitnessWord().equals(pmatch.getWitnessWord()));
      }
    };
    return Iterables.filter(pmatches, unfixedAlternativeToGivenPMatch);
  }

  //NOTE: THIS METHOD IS SPECIFIC FOR PHRASE MATCHING!
  // TODO: should look more like findMatches!
  static Set<Match<Phrase>> match2(final WitnessSegmentPhrases pa, final WitnessSegmentPhrases pb) {
    // take pa as a starting point (depends on the length!)

    final Set<Match<Phrase>> matches = Sets.newLinkedHashSet();
    for (int i = 1; i <= pa.size(); i++) {
      final Phrase phrase = pa.getPhraseOnPosition(i);
      for (int j = 1; j <= pb.size(); j++) {
        final Phrase phrase2 = pb.getPhraseOnPosition(j);
        final Subsegment subsegment1 = phrase.getSubsegment();
        final Subsegment subsegment2 = phrase2.getSubsegment();
        final boolean exactMatch = subsegment1.getTitle().equals(subsegment2.getTitle());
        if (exactMatch) {
          //          System.out.println(phrase);
          //          System.out.println(phrase2);
          matches.add(new Match<Phrase>(phrase, phrase2));
          break;
        } else {
          //          System.out.println(phrase);
          //          System.out.println(phrase2);
        }
      }
    }
    return matches;
  }

}
