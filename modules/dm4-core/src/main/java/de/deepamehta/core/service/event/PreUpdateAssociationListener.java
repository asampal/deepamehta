package de.deepamehta.core.service.event;

import de.deepamehta.core.Association;
import de.deepamehta.core.model.AssociationModel;
import de.deepamehta.core.service.Directives;
import de.deepamehta.core.service.Listener;



public interface PreUpdateAssociationListener extends Listener {

    void preUpdateAssociation(Association assoc, AssociationModel newModel, Directives directives);
}
