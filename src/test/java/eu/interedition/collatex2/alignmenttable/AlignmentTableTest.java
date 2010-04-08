package eu.interedition.collatex2.alignmenttable;

import static org.junit.Assert.assertEquals;

import java.util.List;

import junit.framework.Assert;

import org.junit.BeforeClass;
import org.junit.Test;

import com.google.common.collect.Lists;

import eu.interedition.collatex2.implementation.Factory;
import eu.interedition.collatex2.implementation.alignmenttable.AlignmentTable4;
import eu.interedition.collatex2.implementation.alignmenttable.AlignmentTableCreator3;
import eu.interedition.collatex2.interfaces.IAlignmentTable;
import eu.interedition.collatex2.interfaces.ICallback;
import eu.interedition.collatex2.interfaces.IColumn;
import eu.interedition.collatex2.interfaces.IPhrase;
import eu.interedition.collatex2.interfaces.IWitness;

public class AlignmentTableTest {
  private static Factory factory;

  @BeforeClass
  public static void setUp() {
    factory = new Factory();
  }

  @Test
  public void testFirstWitness() {
    final IWitness w1 = factory.createWitness("A", "the black cat");
    //final WitnessSet set = new WitnessSet(w1);
    final List<IWitness> set = Lists.newArrayList(w1);
    final IAlignmentTable table = factory.createAlignmentTable(set);
    final String expected = "A: the|black|cat\n";
    assertEquals(expected, table.toString());
  }

  @Test
  public void testEverythingMatches() {
    final IWitness w1 = factory.createWitness("A", "the black cat");
    final IWitness w2 = factory.createWitness("B", "the black cat");
    final IWitness w3 = factory.createWitness("C", "the black cat");
    //final WitnessSet set = new WitnessSet(w1, w2, w3);
    final List<IWitness> set = Lists.newArrayList(w1, w2, w3);
    final IAlignmentTable table = factory.createAlignmentTable(set);
    String expected = "A: the|black|cat\n";
    expected += "B: the|black|cat\n";
    expected += "C: the|black|cat\n";
    assertEquals(expected, table.toString());
  }

  @Test
  public void testVariant() {
    final IWitness w1 = factory.createWitness("A", "the black cat");
    final IWitness w2 = factory.createWitness("B", "the white cat");
    final IWitness w3 = factory.createWitness("C", "the green cat");
    final IWitness w4 = factory.createWitness("D", "the red cat");
    final IWitness w5 = factory.createWitness("E", "the yellow cat");
    final List<IWitness> set = Lists.newArrayList(w1, w2, w3, w4, w5);
    final IAlignmentTable table = factory.createAlignmentTable(set);
    String expected = "A: the|black|cat\n";
    expected += "B: the|white|cat\n";
    expected += "C: the|green|cat\n";
    expected += "D: the|red|cat\n";
    expected += "E: the|yellow|cat\n";
    assertEquals(expected, table.toString());
  }

  @Test
  public void testOmission() {
    final IWitness w1 = factory.createWitness("A", "the black cat");
    final IWitness w2 = factory.createWitness("B", "the cat");
    final IWitness w3 = factory.createWitness("C", "the black cat");
    final List<IWitness> set = Lists.newArrayList(w1, w2, w3);
    final IAlignmentTable table = factory.createAlignmentTable(set);
    String expected = "A: the|black|cat\n";
    expected += "B: the| |cat\n";
    expected += "C: the|black|cat\n";
    assertEquals(expected, table.toString());
  }

  //TODO: rename test!
  @Test
  public void testTwoWitnesses() {
    final IWitness w1 = factory.createWitness("A", "the black cat");
    final IWitness w2 = factory.createWitness("B", "the white and black cat");
    final List<IWitness> set = Lists.newArrayList(w1, w2);
    final IAlignmentTable table = factory.createAlignmentTable(set);
    String expected = "A: the| | |black|cat\n";
    expected += "B: the|white|and|black|cat\n";
    assertEquals(expected, table.toString());
  }

  @Test
  public void testAddition() {
    final IWitness w1 = factory.createWitness("A", "the cat");
    final IWitness w2 = factory.createWitness("B", "before the cat");
    final IWitness w3 = factory.createWitness("C", "the black cat");
    final IWitness w4 = factory.createWitness("D", "the cat walks");
    final List<IWitness> set = Lists.newArrayList(w1, w2, w3, w4);
    final IAlignmentTable table = factory.createAlignmentTable(set);
    String expected = "A:  |the| |cat| \n";
    expected += "B: before|the| |cat| \n";
    expected += "C:  |the|black|cat| \n";
    expected += "D:  |the| |cat|walks\n";
    assertEquals(expected, table.toString());
  }

  //TODO: rename test!
  @Test
  public void testGenSuperbase() {
    final IWitness w1 = factory.createWitness("A", "the cat");
    final IWitness w2 = factory.createWitness("B", "before the cat");
    final IWitness w3 = factory.createWitness("C", "the black cat");
    final IWitness w4 = factory.createWitness("D", "just before midnight the cat walks");
    final List<IWitness> set = Lists.newArrayList(w1, w2, w3, w4);
    final IAlignmentTable table = factory.createAlignmentTable(set);
    String expected = "A:  | | |the| |cat| \n";
    expected += "B:  |before| |the| |cat| \n";
    expected += "C:  | | |the|black|cat| \n";
    expected += "D: just|before|midnight|the| |cat|walks\n";

    assertEquals(expected, table.toString());
  }

  //TODO: rewrite test to use addAddition
  @Test
  public void testAddVariantBeforeColumnAndPositions() {
    final AlignmentTable4 table = new AlignmentTable4();
    final IWitness witness = factory.createWitness("A", "two before two after");
    final IWitness temp = factory.createWitness("B", "in between");
    final IPhrase tobeadded = temp.createPhrase(1, 2);
    final ICallback callback = Factory.NULLCALLBACK;
    AlignmentTableCreator3.addWitness(table, witness, callback);
    final IColumn column = table.getColumns().get(2);
    table.addVariantBefore(column, tobeadded);
    final List<IColumn> columns = table.getColumns();
    Assert.assertEquals(1, columns.get(0).getPosition());
    Assert.assertEquals(2, columns.get(1).getPosition());
    Assert.assertEquals(3, columns.get(2).getPosition());
    Assert.assertEquals(4, columns.get(3).getPosition());
    Assert.assertEquals(5, columns.get(4).getPosition());
    Assert.assertEquals(6, columns.get(5).getPosition());
  }

  @Test
  public void testVariation() {
    final IWitness w1 = factory.createWitness("A", "the black cat");
    final IWitness w2 = factory.createWitness("B", "the black and white cat");
    final IWitness w3 = factory.createWitness("C", "the black very special cat");
    final IWitness w4 = factory.createWitness("D", "the black not very special cat");
    final List<IWitness> set = Lists.newArrayList(w1, w2, w3, w4);
    final IAlignmentTable table = factory.createAlignmentTable(set);
    String expected = "A: the|black| | | |cat\n";
    expected += "B: the|black| |and|white|cat\n";
    expected += "C: the|black| |very|special|cat\n";
    expected += "D: the|black|not|very|special|cat\n";
    assertEquals(expected, table.toString());
  }

  @Test
  public void testWitnessReorder() {
    final IWitness w1 = factory.createWitness("A", "the black cat");
    final IWitness w2 = factory.createWitness("B", "the black and white cat");
    final IWitness w3 = factory.createWitness("C", "the black not very special cat");
    final IWitness w4 = factory.createWitness("D", "the black very special cat");
    final List<IWitness> set = Lists.newArrayList(w1, w2, w3, w4);
    final IAlignmentTable table = factory.createAlignmentTable(set);
    String expected = "A: the|black| | | |cat\n";
    expected += "B: the|black|and|white| |cat\n";
    expected += "C: the|black|not|very|special|cat\n";
    expected += "D: the|black| |very|special|cat\n";
    assertEquals(expected, table.toString());
  }

  //Note: tests toString method
  @Test
  public void testStringOutputOneWitness() {
    final IWitness a = factory.createWitness("A", "the black cat");
    final List<IWitness> set = Lists.newArrayList(a);
    final IAlignmentTable table = factory.createAlignmentTable(set);
    final String expected = "A: the|black|cat\n";
    assertEquals(expected, table.toString());
  }

  //Note: tests toString method
  @Test
  public void testStringOutputTwoWitnesses() {
    final IWitness a = factory.createWitness("A", "the black cat");
    final IWitness b = factory.createWitness("B", "the black cat");
    final List<IWitness> set = Lists.newArrayList(a, b);
    final IAlignmentTable table = factory.createAlignmentTable(set);
    String expected = "A: the|black|cat\n";
    expected += "B: the|black|cat\n";
    assertEquals(expected, table.toString());
  }

  //Note: tests toString method
  @Test
  public void testStringOutputEmptyCells() {
    final IWitness a = factory.createWitness("A", "the black cat");
    final IWitness b = factory.createWitness("B", "the");
    final List<IWitness> set = Lists.newArrayList(a, b);
    final IAlignmentTable table = factory.createAlignmentTable(set);
    String expected = "A: the|black|cat\n";
    expected += "B: the| | \n";
    assertEquals(expected, table.toString());
  }

}
