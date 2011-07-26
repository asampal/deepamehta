package de.deepamehta.core.impl.service;

import de.deepamehta.core.Association;
import de.deepamehta.core.AssociationType;
import de.deepamehta.core.RelatedAssociation;
import de.deepamehta.core.RelatedTopic;
import de.deepamehta.core.Role;
import de.deepamehta.core.Topic;
import de.deepamehta.core.TopicRole;
import de.deepamehta.core.Type;
import de.deepamehta.core.model.AssociationModel;
import de.deepamehta.core.model.AssociationRoleModel;
import de.deepamehta.core.model.IndexMode;
import de.deepamehta.core.model.RelatedAssociationModel;
import de.deepamehta.core.model.RelatedTopicModel;
import de.deepamehta.core.model.RoleModel;
import de.deepamehta.core.model.SimpleValue;
import de.deepamehta.core.model.TopicRoleModel;
import de.deepamehta.core.service.Directive;
import de.deepamehta.core.service.Directives;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import java.util.logging.Logger;



/**
 * An association that is attached to the {@link DeepaMehtaService}.
 */
class AttachedAssociation extends AttachedDeepaMehtaObject implements Association {

    // ---------------------------------------------------------------------------------------------- Instance Variables

    private Role role1;
    private Role role2;

    private Logger logger = Logger.getLogger(getClass().getName());

    // ---------------------------------------------------------------------------------------------------- Constructors

    AttachedAssociation(EmbeddedService dms) {
        super(dms);     // ### The model and viewConfig remain uninitialized.
                        // ### They are initialized later on through fetch().
    }

    AttachedAssociation(AssociationModel model, EmbeddedService dms) {
        super(model, dms);
        this.role1 = createAttachedRole(model.getRoleModel1());
        this.role2 = createAttachedRole(model.getRoleModel2());
    }

    // -------------------------------------------------------------------------------------------------- Public Methods



    // ******************************************
    // *** AttachedDeepaMehtaObject Overrides ***
    // ******************************************



    @Override
    public void setTypeUri(String assocTypeUri) {
        // update memory
        super.setTypeUri(assocTypeUri);
        // update DB
        storeTypeUri();
    }

    // === Implementation of the abstract methods ===

    @Override
    protected String className() {
        return "association";
    }

    @Override
    protected void storeUri(String uri) {
        dms.storage.setAssociationUri(getId(), uri);
    }

    @Override
    protected SimpleValue storeValue(SimpleValue value) {
        return dms.storage.setAssociationValue(getId(), value);
    }

    @Override
    protected void indexValue(IndexMode indexMode, String indexKey, SimpleValue value, SimpleValue oldValue) {
        dms.storage.indexAssociationValue(getId(), indexMode, indexKey, value, oldValue);
    }

    @Override
    protected Type getType() {
        return dms.getAssociationType(getTypeUri(), null);    // FIXME: clientContext=null
    }

    @Override
    protected RoleModel getRoleModel(String roleTypeUri) {
        return new AssociationRoleModel(getId(), roleTypeUri);
    }

    // === Deletion ===

    @Override
    public void delete(Directives directives) {
        // delete sub-topics and associations
        super.delete(directives);
        // delete association itself
        logger.info("Deleting " + this);
        dms.storage.deleteAssociation(getId());
        directives.add(Directive.DELETE_ASSOCIATION, this);
    }



    // **********************************
    // *** Association Implementation ***
    // **********************************



    @Override
    public Role getRole1() {
        return role1;
    }

    @Override
    public Role getRole2() {
        return role2;
    }

    // ---

    // compare to Neo4jMehtaEdge.getMehtaObject()
    @Override
    public Role getRole(long objectId) {
        long id1 = getObjectId(((AttachedRole) getRole1()).getModel());
        long id2 = getObjectId(((AttachedRole) getRole2()).getModel());
        //
        if (id1 == objectId && id2 == objectId) {
            throw new RuntimeException("Self-connected mehta objects are not supported (" + this + ")");
        }
        //
        if (id1 == objectId) {
            return getRole1();
        } else if (id2 == objectId) {
            return getRole2();
        } else {
            throw new RuntimeException("Topic/Association " + objectId + " plays no role in " + this);
        }
    }

    // ---

    public AssociationModel getModel() {
        return (AssociationModel) super.getModel();
    }



    // === Traversal ===

    @Override
    public Topic getTopic(String roleTypeUri) {
        Set<Topic> topics = getTopics(roleTypeUri);
        switch (topics.size()) {
        case 0:
            return null;
        case 1:
            return topics.iterator().next();
        default:
            throw new RuntimeException("Ambiguity in association: " + topics.size() + " topics have role type \"" +
                roleTypeUri + "\" (" + this + ")");
        }
    }

    @Override
    public Set<Topic> getTopics(String roleTypeUri) {
        Set<Topic> topics = new HashSet();
        filterTopic(getRole1(), roleTypeUri, topics);
        filterTopic(getRole2(), roleTypeUri, topics);
        return topics;
    }

    // ---

    @Override
    public RelatedAssociation getRelatedAssociation(String assocTypeUri, String myRoleTypeUri,
                                                    String othersRoleTypeUri) {
        RelatedAssociationModel relAssoc = dms.storage.getAssociationRelatedAssociation(getId(),
            assocTypeUri, myRoleTypeUri, othersRoleTypeUri);
        return relAssoc != null ? dms.attach(relAssoc) : null;
    }



    // ***************************************
    // *** DeepaMehtaObject Implementation ***
    // ***************************************



    // === Traversal ===

    // --- Topic Retrieval ---

    @Override
    public Set<RelatedTopic> getRelatedTopics(List assocTypeUris, String myRoleTypeUri, String othersRoleTypeUri,
                                    String othersTopicTypeUri, boolean fetchComposite, boolean fetchRelatingComposite) {
        Set<RelatedTopicModel> topics = dms.storage.getAssociationRelatedTopics(getId(), assocTypeUris, myRoleTypeUri,
            othersRoleTypeUri, othersTopicTypeUri);
        return dms.attach(topics, fetchComposite, fetchRelatingComposite);
    }

    // --- Association Retrieval ---

    @Override
    public Set<Association> getAssociations(String myRoleTypeUri) {
        return dms.attach(dms.storage.getAssociationAssociations(getId(), myRoleTypeUri));
    }



    // ----------------------------------------------------------------------------------------- Package Private Methods

    @Override
    void store() {
        dms.storage.createAssociation(getModel());
        dms.associateWithAssociationType(getModel());
        super.store();
    }

    /**
     * @param   assocModel  The data to update.
     *                      If the type URI is <code>null</code> it is not updated.
     *                      If role 1 is <code>null</code> it is not updated.
     *                      If role 2 is <code>null</code> it is not updated.
     */
    AssociationChangeReport update(AssociationModel assocModel) {
        logger.info("Updating association " + getId() + " (new " + assocModel + ")");
        //
        super.update(assocModel);
        //
        AssociationChangeReport report = new AssociationChangeReport();
        boolean typeUriChanged = false;
        boolean roleType1Changed = false;
        boolean roleType2Changed = false;
        //
        // 1) update type
        String newTypeUri = assocModel.getTypeUri();
        if (newTypeUri != null) {
            typeUriChanged = updateType(newTypeUri, report);
        }
        // 2) update role type 1
        RoleModel roleModel1 = assocModel.getRoleModel1();
        if (roleModel1 != null) {
            roleType1Changed = updateRole(roleModel1, 1);
        }
        // 3) update role type 2
        RoleModel roleModel2 = assocModel.getRoleModel2();
        if (roleModel2 != null) {
            roleType2Changed = updateRole(roleModel2, 2);
        }
        //
        if (!typeUriChanged && !roleType1Changed && !roleType2Changed) {
            logger.info("Updating association " + getId() + " ABORTED -- no changes made by user");
        }
        //
        return report;
    }

    /**
     * Convenience method.
     */
    AssociationType getAssociationType() {
        return (AssociationType) getType();
    }



    // ------------------------------------------------------------------------------------------------- Private Methods

    private void filterTopic(Role role, String roleTypeUri, Set<Topic> topics) {
        if (role instanceof TopicRole && role.getRoleTypeUri().equals(roleTypeUri)) {
            topics.add(fetchRoleTopic((TopicRole) role));
        }
    }

    // FIXME: move to AttachedTopicRole / extend TopicRole interface?
    private Topic fetchRoleTopic(TopicRole role) {
        if (role.topicIdentifiedByUri()) {
            return dms.getTopic("uri", new SimpleValue(role.getTopicUri()), false);     // fetchComposite=false
        } else {
            return dms.getTopic(role.getTopicId(), false, null);    // fetchComposite=false, clientContext=null
        }
    }



    // === Store ===

    private void storeTypeUri() {
        // remove current assignment
        long assocId = fetchTypeTopic().getAssociation().getId();
        dms.deleteAssociation(assocId, null);  // clientContext=null
        // create new assignment
        dms.associateWithAssociationType(getModel());
    }    

    private RelatedTopic fetchTypeTopic() {
        // assocTypeUri=null (supposed to be "dm4.core.instantiation" but not possible ### explain)
        return getRelatedTopic(null, "dm4.core.instance", "dm4.core.type", "dm4.core.assoc_type",
            false, false);     // fetchComposite=false
    }



    // === Helper ===

    private boolean updateType(String newTypeUri, AssociationChangeReport report) {
        String typeUri = getTypeUri();      // current value
        if (!typeUri.equals(newTypeUri)) {  // has changed?
            logger.info("Changing type from \"" + typeUri + "\" -> \"" + newTypeUri + "\"");
            report.typeUriChanged(typeUri, newTypeUri);
            setTypeUri(newTypeUri);
            return true;
        }
        return false;
    }

    /**
     * @param   nr      used only for logging
     */
    private boolean updateRole(RoleModel newModel, int nr) {
        // Note: We must lookup the roles individually.
        // The role order (getRole1(), getRole2()) is undeterministic and not fix.
        Role role = getRole(getObjectId(newModel));
        String newRoleTypeUri = newModel.getRoleTypeUri();  // new value
        String roleTypeUri = role.getRoleTypeUri();         // current value
        if (!roleTypeUri.equals(newRoleTypeUri)) {          // has changed?
            logger.info("Changing role type " + nr + " from \"" + roleTypeUri + "\" -> \"" + newRoleTypeUri + "\"");
            role.setRoleTypeUri(newRoleTypeUri);
            return true;
        }
        return false;
    }

    // ---

    private Role createAttachedRole(RoleModel model) {
        if (model instanceof TopicRoleModel) {
            return new AttachedTopicRole((TopicRoleModel) model, this, dms);
        } else if (model instanceof AssociationRoleModel) {
            return new AttachedAssociationRole((AssociationRoleModel) model, this, dms);
        } else {
            throw new RuntimeException("Unexpected RoleModel object (" + model + ")");
        }
    }

    // ### TODO: probably a generic getId() should be added to the Role interface.
    private long getObjectId(RoleModel model) {
        if (model instanceof TopicRoleModel) {
            return ((TopicRoleModel) model).getTopicId();
        } else if (model instanceof AssociationRoleModel) {
            return ((AssociationRoleModel) model).getAssociationId();
        } else {
            throw new RuntimeException("Unexpected RoleModel object (" + model + ")");
        }
    }
}