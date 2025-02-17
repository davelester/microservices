package eu.interedition.web.markup;

import org.neo4j.graphdb.Node;

import static eu.interedition.web.markup.MarkupRelationshipType.NAMES_ANNOTATION_TYPE;
import static org.neo4j.graphdb.Direction.INCOMING;

/**
 * @author <a href="http://gregor.middell.net/" title="Homepage">Gregor Middell</a>
 */
public class AnnotationType extends DocumentedType {
    public AnnotationType(Node node) {
        super(node);
    }

    public AnnotationType(Node node, String documentation) {
        super(node, documentation);
    }

    public Name getName() {
        return new Name(node.getSingleRelationship(NAMES_ANNOTATION_TYPE, INCOMING).getStartNode());
    }

    public boolean isTextContainer() {
        return (Boolean) node.getProperty("text_container", false);
    }

    public void setTextContainer(boolean textContainer) {
        if (textContainer) {
            node.setProperty("text_container", textContainer);
        } else {
            node.removeProperty("text_container");
        }
    }
}
