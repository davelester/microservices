package eu.interedition.web.io;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import eu.interedition.collatex.Witness;
import eu.interedition.collatex.Token;
import eu.interedition.collatex.input.WhitespaceTokenizer;
import eu.interedition.collatex.input.SimpleWitness;
import eu.interedition.web.collatex.Collation;
import eu.interedition.web.collatex.WebToken;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;

import java.io.IOException;
import java.util.List;
import java.util.SortedSet;

/**
 * @author <a href="http://gregor.middell.net/" title="Homepage">Gregor Middell</a>
 */
public class CollationHttpMessageConverter extends AbstractHttpMessageConverter<Collation> {

  private ObjectMapper objectMapper = new ObjectMapper();

  public CollationHttpMessageConverter() {
    super(MediaType.APPLICATION_JSON);
  }

  @Override
  protected boolean supports(Class<?> clazz) {
    return Collation.class.isAssignableFrom(clazz);
  }

  @Override
  public boolean canWrite(Class<?> clazz, MediaType mediaType) {
    return false;
  }

  @Override
  protected Collation readInternal(Class<? extends Collation> clazz, HttpInputMessage inputMessage) throws IOException, HttpMessageNotReadableException {
    final JsonNode collationNode = objectMapper.readTree(inputMessage.getBody());
    final JsonNode witnessesNode = collationNode.path("witnesses");
    if (witnessesNode.isMissingNode() || !witnessesNode.isArray()) {
      throw new HttpMessageNotReadableException("Expecting 'witnesses' array");
    }
    
    List<Iterable<Token>> witnesses = Lists.newArrayList();
    for (JsonNode witnessNode : witnessesNode) {
      if (!witnessNode.isObject()) {
        throw new HttpMessageNotReadableException("Expecting witness object");
      }
      final JsonNode witnessIdNode = witnessNode.path("id");
      if (witnessIdNode.isMissingNode() || !witnessIdNode.isTextual()) {
        throw new HttpMessageNotReadableException("Expected textual witness 'id'");
      }
      final String witnessIdStr = witnessIdNode.getTextValue().trim();
      if (witnessIdStr.length() == 0) {
        throw new HttpMessageNotReadableException("Empty witness 'id' encountered");
      }

      final SimpleWitness witness = new SimpleWitness(witnessIdStr);
      if (witnesses.contains(witness)) {
        throw new HttpMessageNotReadableException(String.format("Duplicate sigil for witness '%s", witness));
      }

      final JsonNode contentNode = witnessNode.path("content");
      final JsonNode tokensNode = witnessNode.path("tokens");
      if (contentNode.isMissingNode() && tokensNode.isMissingNode()) {
        throw new HttpMessageNotReadableException(String.format("Expected either 'tokens' or 'content' field in witness \"%s\"", witness));
      }
      
      if (!tokensNode.isMissingNode()) {
        if (!tokensNode.isArray()) {
          throw new HttpMessageNotReadableException(String.format("Expected 'tokens' array in witness \"%s\"", witness));
        }
        List<Token> tokens = Lists.newArrayList();
        for (JsonNode tokenNode : tokensNode) {
          if (!tokenNode.isObject()) {
            throw new HttpMessageNotReadableException(String.format("Expected token object in 'tokens' field in witness \"%s\"", witness));
          }
          final JsonNode tokenContentNode = tokenNode.path("t");
          if (tokenContentNode.isMissingNode() || !tokenContentNode.isTextual()) {
            throw new HttpMessageNotReadableException(String.format("Expected textual token content field 't' in witness \"%s\"", witness));
          }
          final String tokenContent = tokenContentNode.getTextValue();
          String normalizedTokenContent;
          final JsonNode normalizedTokenContentNode = tokenNode.path("n");
          if (normalizedTokenContentNode.isMissingNode()) {
            normalizedTokenContent = SimpleWitness.TOKEN_NORMALIZER.apply(tokenContent);
          } else {
            if (!normalizedTokenContentNode.isTextual()) {
              throw new HttpMessageNotReadableException(String.format("Expected textual normalized token content in witness \"%s\"", witness));
            }
            normalizedTokenContent = normalizedTokenContentNode.getTextValue();
          }

          if (normalizedTokenContent.length() == 0) {
            throw new HttpMessageNotReadableException(String.format("Empty token encountered in witness \"%s\"", witness));
          }
          
          tokens.add(new WebToken(witness, tokens.size(), tokenContent, normalizedTokenContent, tokenNode));
        }
        witness.setTokens(tokens);
      } else {
        if (!contentNode.isTextual()) {
          throw new HttpMessageNotReadableException(String.format("Expected 'content' text field in witness \"%s\"", witness));
        }
        witness.setTokenContents(new WhitespaceTokenizer().apply(contentNode.getTextValue()));
      }
      
      if (witness.getTokens().isEmpty()) {
        throw new HttpMessageNotReadableException(String.format("No tokens in witness \"%s\"", witness));
      }      
      witnesses.add(witness);
    }

    if (witnesses.isEmpty()) {
      throw new HttpMessageNotReadableException("No witnesses in collation");
    }

    return new Collation(witnesses);
  }

  @Override
  protected void writeInternal(Collation collation, HttpOutputMessage outputMessage) throws IOException, HttpMessageNotWritableException {
    throw new HttpMessageNotWritableException(collation.toString());
  }
}
