package de.deepamehta.core.model;

import org.codehaus.jettison.json.JSONObject;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;



/**
 * @author <a href="mailto:jri@deepamehta.de">Jörg Richter</a>
 */
public class TopicTypeDefinition {

    // ---------------------------------------------------------------------------------------------- Instance Variables

    private String topicTypeUri;
    private Map<String, AssociationDefinition> assocDefs;

    private Logger logger = Logger.getLogger(getClass().getName());

    // ---------------------------------------------------------------------------------------------------- Constructors

    public TopicTypeDefinition(String topicTypeUri) {
        this.topicTypeUri = topicTypeUri;
        this.assocDefs = new HashMap();
    }

    // -------------------------------------------------------------------------------------------------- Public Methods

    public String getUri() {
        return topicTypeUri;
    }

    public AssociationDefinition getAssociationDefinition(String assocDefUri) {
        AssociationDefinition assocDef = assocDefs.get(assocDefUri);
        if (assocDef == null) {
            throw new RuntimeException("Association definition \"" + assocDefUri + "\" not found (in " + this + ")");
        }
        return assocDef;
    }

    // FIXME: abstraction. Adding should be the factory's resposibility
    public void addAssociationDefinition(AssociationDefinition assocDef) {
        String assocDefUri = assocDef.getUri();
        AssociationDefinition existing = assocDefs.get(assocDefUri);
        if (existing != null) {
            throw new RuntimeException("Ambiguity: topic type definition \"" + topicTypeUri + "\" has more than " +
                "one association definitions with uri \"" + assocDefUri + "\" -- Use distinct part role types or " +
                "specifiy an unique uri");
        }
        assocDefs.put(assocDefUri, assocDef);
    }

    // ---

    @Override
    public String toString() {
        return "topic type definition (uri=\"" + topicTypeUri + "\", assocDefs=" + assocDefs + ")";
    }
}
