package eu.interedition.collatex.lab;

import eu.interedition.collatex.Token;

import java.util.SortedSet;

/**
 * @author <a href="http://gregor.middell.net/" title="Homepage">Gregor Middell</a>
 */
public class EditGraphVertexModel {
  private final SortedSet<Token> base;
  private final Token witness;

  public EditGraphVertexModel(Token witness, SortedSet<Token> base) {
    this.base = base;
    this.witness = witness;
  }

  public SortedSet<Token> getBase() {
    return base;
  }

  public Token getWitness() {
    return witness;
  }
}
