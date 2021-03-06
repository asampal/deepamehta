package de.deepamehta.core.model;

import org.codehaus.jettison.json.JSONObject;
import org.codehaus.jettison.json.JSONArray;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;



/**
 * Definition of an association between 2 topic types -- part of DeepaMehta's type system,
 * like an association in a class diagram. Used to represent both, aggregations and compositions.
 * ### FIXDOC: also assoc types have assoc defs
 *
 * @author <a href="mailto:jri@deepamehta.de">Jörg Richter</a>
 */
public class AssociationDefinitionModel extends AssociationModel {

    // ---------------------------------------------------------------------------------------------- Instance Variables

    private String instanceLevelAssocTypeUri;

    private String wholeTypeUri;
    private String partTypeUri;

    private String wholeRoleTypeUri;    // fixed: "dm4.core.whole"
    private String partRoleTypeUri;     // fixed: "dm4.core.part"

    private String wholeCardinalityUri;
    private String partCardinalityUri;

    private ViewConfigurationModel viewConfigModel;   // is never null

    private Logger logger = Logger.getLogger(getClass().getName());

    // ---------------------------------------------------------------------------------------------------- Constructors

    public AssociationDefinitionModel(String typeUri, String wholeTypeUri, String partTypeUri,
                                                      String wholeCardinalityUri, String partCardinalityUri) {
        this(-1, typeUri, wholeTypeUri, partTypeUri, wholeCardinalityUri, partCardinalityUri, null);
    }

    public AssociationDefinitionModel(long id, String typeUri, String wholeTypeUri, String partTypeUri,
                                                               String wholeCardinalityUri, String partCardinalityUri,
                                                               ViewConfigurationModel viewConfigModel) {
        super(id, typeUri);
        //
        this.wholeTypeUri = wholeTypeUri;
        this.partTypeUri = partTypeUri;
        // set default role types
        this.wholeRoleTypeUri = "dm4.core.whole";   // ### wholeRoleTypeUri != null ? wholeRoleTypeUri : wholeTypeUri;
        this.partRoleTypeUri = "dm4.core.part";     // ### partRoleTypeUri != null ? partRoleTypeUri : partTypeUri;
        //
        this.wholeCardinalityUri = wholeCardinalityUri;
        this.partCardinalityUri = partCardinalityUri;
        // derive uri
        this.uri = partTypeUri;                     // ### partRoleTypeUri;
        //
        this.viewConfigModel = viewConfigModel != null ? viewConfigModel : new ViewConfigurationModel();
        //
        initAssociationModel();
        initInstanceLevelAssocTypeUri();
    }

    public AssociationDefinitionModel(JSONObject assocDef, String wholeTypeUri) {
        super(-1, null);
        try {
            this.id = assocDef.optLong("id", -1);
            this.typeUri = assocDef.getString("assoc_type_uri");
            //
            this.wholeTypeUri = wholeTypeUri;
            this.partTypeUri = assocDef.getString("part_type_uri");
            //
            this.wholeRoleTypeUri = "dm4.core.whole";   // ### assocDef.optString("whole_role_type_uri", wholeTypeUri);
            this.partRoleTypeUri = "dm4.core.part";     // ### assocDef.optString("part_role_type_uri", partTypeUri);
            //
            this.uri = partTypeUri;                     // ### partRoleTypeUri;
            //
            if (!assocDef.has("whole_cardinality_uri") && !typeUri.equals("dm4.core.composition_def")) {
                throw new RuntimeException("\"whole_cardinality_uri\" is missing");
            }
            this.wholeCardinalityUri = assocDef.optString("whole_cardinality_uri", "dm4.core.one");
            this.partCardinalityUri = assocDef.getString("part_cardinality_uri");
            //
            this.viewConfigModel = new ViewConfigurationModel(assocDef);
            //
            initAssociationModel();
            initInstanceLevelAssocTypeUri();
        } catch (Exception e) {
            throw new RuntimeException("Parsing AssociationDefinitionModel failed (JSONObject=" + assocDef + ")", e);
        }
    }

    // -------------------------------------------------------------------------------------------------- Public Methods

    public String getInstanceLevelAssocTypeUri() {
        return instanceLevelAssocTypeUri;
    }

    public String getWholeTypeUri() {
        return wholeTypeUri;
    }

    public String getPartTypeUri() {
        return partTypeUri;
    }

    public String getWholeRoleTypeUri() {
        return wholeRoleTypeUri;
    }

    public String getPartRoleTypeUri() {
        return partRoleTypeUri;
    }

    public String getWholeCardinalityUri() {
        return wholeCardinalityUri;
    }

    public String getPartCardinalityUri() {
        return partCardinalityUri;
    }

    public ViewConfigurationModel getViewConfigModel() {
        return viewConfigModel;
    }

    // ---

    @Override
    public void setTypeUri(String typeUri) {
        super.setTypeUri(typeUri);
        initInstanceLevelAssocTypeUri();
    }

    public void setWholeCardinalityUri(String wholeCardinalityUri) {
        this.wholeCardinalityUri = wholeCardinalityUri;
    }

    public void setPartCardinalityUri(String partCardinalityUri) {
        this.partCardinalityUri = partCardinalityUri;
    }

    public void setViewConfigModel(ViewConfigurationModel viewConfigModel) {
        this.viewConfigModel = viewConfigModel;
    }

    // ---

    public JSONObject toJSON() {
        try {
            JSONObject o = super.toJSON();
            // o.put("id", id);                 // ### FIXME: drop this
            // o.put("uri", uri);               // ### FIXME: drop this
            o.put("assoc_type_uri", typeUri);   // ### FIXME: drop this
            o.put("whole_type_uri", wholeTypeUri);
            o.put("part_type_uri", partTypeUri);
            o.put("whole_role_type_uri", wholeRoleTypeUri);
            o.put("part_role_type_uri", partRoleTypeUri);
            o.put("whole_cardinality_uri", wholeCardinalityUri);
            o.put("part_cardinality_uri", partCardinalityUri);
            viewConfigModel.toJSON(o);
            return o;
        } catch (Exception e) {
            throw new RuntimeException("Serialization failed (" + this + ")", e);
        }
    }

    // ---

    @Override
    public String toString() {
        return "\n    association definition (" + super.toString() +
            ")\n        pos 1: (type=\"" + wholeTypeUri + "\", role=\"" + wholeRoleTypeUri +
            "\", cardinality=\"" + wholeCardinalityUri +
            "\")\n        pos 2: (type=\"" + partTypeUri + "\", role=\"" + partRoleTypeUri +
            "\", cardinality=\"" + partCardinalityUri +
            "\")\n        association definition " + viewConfigModel;
    }

    // ----------------------------------------------------------------------------------------- Package Private Methods

    static void toJSON(Collection<AssociationDefinitionModel> assocDefs, JSONObject o) throws Exception {
        List assocDefList = new ArrayList();
        for (AssociationDefinitionModel assocDef : assocDefs) {
            assocDefList.add(assocDef.toJSON());
        }
        o.put("assoc_defs", assocDefList);
    }

    // ------------------------------------------------------------------------------------------------- Private Methods

    private void initAssociationModel() {
        setRoleModel1(new TopicRoleModel(wholeTypeUri, "dm4.core.whole_type"));
        setRoleModel2(new TopicRoleModel(partTypeUri,  "dm4.core.part_type"));
    }

    private void initInstanceLevelAssocTypeUri() {
        if (typeUri.equals("dm4.core.aggregation_def")) {
            this.instanceLevelAssocTypeUri = "dm4.core.aggregation";
        } else if (typeUri.equals("dm4.core.composition_def")) {
            this.instanceLevelAssocTypeUri = "dm4.core.composition";
        } else {
            throw new RuntimeException("Unexpected association type URI: \"" + typeUri + "\"");
        }
    }
}
