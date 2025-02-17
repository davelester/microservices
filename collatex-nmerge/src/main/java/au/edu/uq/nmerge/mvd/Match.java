/*
 * NMerge is Copyright 2009-2011 Desmond Schmidt
 *
 * This file is part of NMerge. NMerge is a Java library for merging
 * multiple versions into multi-version documents (MVDs), and for
 * reading, searching and comparing them.
 *
 * NMerge is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package au.edu.uq.nmerge.mvd;

import com.google.common.base.Objects;
import com.google.common.base.Predicate;
import com.google.common.collect.Lists;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Represent one Pair in an MVD
 *
 * @author Desmond Schmidt 18/8/07
 */
public class Match<T> {
  public static int pairId = 1;

  /**
   * parent id if subject of a transposition
   */
  private int id;

  private Match<T> parent;
  private List<Match<T>> children = Lists.newArrayList();

  public Set<Witness> witnesses;
  private List<T> tokens;

  /**
   * Create a basic pair
   *
   * @param witnesses its versions
   * @param tokens    its data
   */
  public Match(Set<Witness> witnesses, List<T> tokens) {
    this.witnesses = witnesses;
    this.tokens = tokens;
  }

  public int getId() {
    return id;
  }

  public void setId(int id) {
    this.id = id;
  }


  public List<Match<T>> getChildren() {
    return Collections.unmodifiableList(children);
  }

  /**
   * Get the number of children we have
   *
   * @return the current size of the children list
   */
  public int numChildren() {
    return children.size();
  }

  /**
   * Add a child pair to this parent to be. Children
   * don't have any data.
   *
   * @param child the child to add
   */
  public void addChild(Match<T> child) {
    children.add(child);
    child.setParent(this);
  }

  /**
   * Remove a child pair. If this was our only child, stop
   * being a parent.
   *
   * @param child the child to remove
   */
  public void removeChild(Match<T> child) {
    children.remove(child);
  }

  /**
   * Set the pair's parent i.e. make this a child
   *
   * @param parent the parent to be
   */
  public void setParent(Match<T> parent) {
    this.parent = parent;
  }

  /**
   * Just get the length of the data, even if it is transposed.
   *
   * @return the length of the pair in bytes
   */
  int length() {
    return (parent != null) ? parent.length() : tokens.size();
  }

  /**
   * Does this pair contain the given version?
   *
   * @param version the version to test
   * @return true if version intersects with this pair
   */
  public boolean contains(Witness version) {
    return witnesses.contains(version);
  }

  /**
   * Is this pair really a hint?
   *
   * @return true if it is, false otherwise
   */
  public boolean isHint() {
    return witnesses.isEmpty();
  }

  /**
   * Is this pair a child, i.e. the object of a transposition?
   *
   * @return true if it is, false otherwise
   */
  public boolean isChild() {
    return parent != null;
  }

  /**
   * Is this pair a parent i.e. the subject of a transposition?
   *
   * @return true if it is, false otherwise
   */
  public boolean isParent() {
    return !children.isEmpty();
  }

  @Override
  public String toString() {
    return Objects.toStringHelper(this)
            .add("witnesses", witnesses)
            .add("parent", parent)
            .add("children", children.size())
            .add("tokens", tokens)
            .toString();
  }

  /**
   * Get the parent of this child pair.
   *
   * @return the parent
   */
  public Match<T> getParent() {
    return parent;
  }

  /**
   * Get the data of this pair
   *
   * @return this pair's data or that of its parent
   */
  public List<T> getTokens() {
    if (parent != null) {
      return parent.getTokens();
    } else {
      return tokens;
    }
  }

  /**
   * Set the data of this pair. Not to be used publicly!
   *
   * @param tokens the new data for this pair
   */
  void setTokens(List<T> tokens) {
    this.tokens = tokens;
  }

  /**
   * Get the child of a parent
   *
   * @param v the version to look for a child in
   * @return the relevant pair or null
   */
  Match<T> getChildInVersion(Witness v) {
    for (Match<T> q : children) {
      if (q.contains(v)) {
        return q;
      }
    }
    return null;
  }

  public static class WitnessPredicate implements Predicate<Match> {

    private final Witness witness;

    public WitnessPredicate(Witness witness) {
      this.witness = witness;
    }

    @Override
    public boolean apply(Match input) {
      return input.witnesses.contains(witness);
    }
  }
}
