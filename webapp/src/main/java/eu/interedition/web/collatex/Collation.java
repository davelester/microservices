/**
 * CollateX - a Java library for collating textual sources,
 * for example, to produce an apparatus.
 *
 * Copyright (C) 2010 ESF COST Action "Interedition".
 *
 * This program is free software: you can redistribute it and/or modify
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

package eu.interedition.web.collatex;

import eu.interedition.collatex.Token;
import eu.interedition.collatex.Witness;
import eu.interedition.collatex.input.SimpleWitness;
import sun.java2d.pipe.SpanShapeRenderer;

import java.util.List;
import java.util.SortedSet;

public class Collation {

  private final List<Iterable<Token>> witnesses;

  public Collation(List<Iterable<Token>> witnesses) {
    this.witnesses = witnesses;
  }

  public List<Iterable<Token>> getWitnesses() {
    return witnesses;
  }
}
