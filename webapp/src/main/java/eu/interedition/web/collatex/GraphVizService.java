package eu.interedition.web.collatex;

import com.google.common.base.Preconditions;
import com.google.common.io.ByteStreams;
import com.google.common.io.Closeables;
import com.google.common.io.FileBackedOutputStream;
import eu.interedition.collatex.graph.VariantGraph;
import eu.interedition.collatex.graph.VariantGraphEdge;
import eu.interedition.collatex.graph.VariantGraphTransposition;
import eu.interedition.collatex.graph.VariantGraphVertex;
import org.neo4j.graphdb.Transaction;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.charset.Charset;

/**
 * @author <a href="http://gregor.middell.net/" title="Homepage">Gregor Middell</a>
 */
@Service
public class GraphVizService implements InitializingBean {

  private final String configuredDotPath = System.getProperty("collatex.graphviz.dot", "/usr/bin/dot");
  private String dotPath;

  public String getConfiguredDotPath() {
    return configuredDotPath;
  }

  public void toDot(VariantGraph graph, Writer writer) {
    final Transaction tx = graph.newTransaction();
    try {
      final PrintWriter out = new PrintWriter(writer);
      final String indent = "  ";
      final String connector = " -> ";

      out.println("digraph G {");

      for (VariantGraphVertex v : graph.vertices()) {
        out.print(indent + "v" + v.getNode().getId());
        out.print(" [label = \"" + toLabel(v) + "\"]");
        out.println(";");
      }

      for (VariantGraphEdge e : graph.edges()) {
        out.print(indent + "v" + e.from().getNode().getId() + connector + "v" + e.to().getNode().getId());
        out.print(" [label = \"" + toLabel(e) + "\"]");
        out.println(";");
      }

      for (VariantGraphTransposition t : graph.transpositions()) {
        out.print(indent + "v" + t.from().getNode().getId() + connector + "v" + t.to().getNode().getId());
        out.print(" [color = \"lightgray\", style = \"dashed\" arrowhead = \"none\", arrowtail = \"none\" ]");
        out.println(";");
      }

      out.println("}");

      out.flush();
      tx.success();
    } finally {
      tx.finish();
    }
  }

  private String toLabel(VariantGraphEdge e) {
    return VariantGraphEdge.TO_CONTENTS.apply(e).replaceAll("\"", "\\\"");
  }

  private String toLabel(VariantGraphVertex v) {
    return VariantGraphVertex.TO_CONTENTS.apply(v).replaceAll("\"", "\\\"");
  }

  public boolean isSvgAvailable() {
    return (dotPath != null);
  }

  public void toSvg(VariantGraph vg, OutputStream out) throws IOException {
    Preconditions.checkState(isSvgAvailable());

    final FileBackedOutputStream dotBuf = new FileBackedOutputStream(102400);

    Writer dotWriter = null;
    try {
      toDot(vg, dotWriter = new OutputStreamWriter(dotBuf, Charset.forName("UTF-8")));
    } finally {
      Closeables.close(dotWriter, false);
    }

    final Process dotProc = Runtime.getRuntime().exec(dotPath + " -Grankdir=LR -Gid=VariantGraph -Tsvg");
    final OutputStream dotStdin = new BufferedOutputStream(dotProc.getOutputStream());
    try {
      ByteStreams.copy(dotBuf.getSupplier(), dotStdin);
    } finally {
      Closeables.close(dotStdin, false);
      dotBuf.reset();
    }

    InputStream svgResult = null;
    final FileBackedOutputStream svgBuf = new FileBackedOutputStream(102400);
    try {
      ByteStreams.copy(svgResult = new BufferedInputStream(dotProc.getInputStream()), svgBuf);
    } finally {
      Closeables.close(svgBuf, false);
      Closeables.close(svgResult, false);
    }

    InputStream svgSource = null;
    try {
      if (dotProc.waitFor() == 0) {
        ByteStreams.copy(svgSource = svgBuf.getSupplier().getInput(), out);
        out.flush();
        return;
      }
    } catch (InterruptedException e) {
    } finally {
      Closeables.closeQuietly(svgSource);
      svgBuf.reset();
    }
  }

  @Override
  public void afterPropertiesSet() throws Exception {
    final File dotExecutable = new File(configuredDotPath);
    this.dotPath = (dotExecutable.canExecute() ? dotExecutable.getCanonicalPath() : null);
  }
}
