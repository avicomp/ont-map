package ru.avicomp.map;

import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.vocabulary.RDFS;
import ru.avicomp.map.spin.vocabulary.AVC;
import ru.avicomp.map.spin.vocabulary.SP;
import ru.avicomp.map.spin.vocabulary.SPINMAP;
import ru.avicomp.map.utils.OntObjects;
import ru.avicomp.ontapi.jena.OntModelFactory;
import ru.avicomp.ontapi.jena.model.OntGraphModel;
import ru.avicomp.ontapi.jena.model.OntID;
import ru.avicomp.ontapi.jena.model.OntNDP;
import ru.avicomp.ontapi.jena.vocabulary.RDF;

/**
 * Created by @szuev on 07.04.2018.
 *
 * @see AVC
 */
@Deprecated // todo: it is temporary and will be remove
public class AVCLibraryBulder {

    public static void main(String... args) {
        PrefixMapping pm = PrefixMapping.Factory.create()
                .setNsPrefixes(OntModelFactory.STANDARD)
                .setNsPrefix("avc", AVC.NS)
                .setNsPrefix("sp", SP.NS)
                .setNsPrefix("spinmap", SPINMAP.NS)
                .lock();
        OntGraphModel m = OntModelFactory.createModel();
        m.setNsPrefixes(pm);
        OntID id = m.setID(AVC.BASE_URI);
        id.setVersionIRI(AVC.BASE_URI + "#1.0");
        OntObjects.setComment(id, "This is an addition to the spin-family in order to customize spin-function behaviour in GUI.");
        id.addAnnotation(OntObjects.versionInfo(m), "version 1.0", null);

        OntNDP hidden = OntObjects.createOWLDataProperty(m, AVC.hidden.getURI());
        hidden.addRange(OntObjects.xsdString(m));

        SP.resource("abs").inModel(m).addProperty(hidden, "Duplicates the function fn:abs, which is preferable, since it has information about return types.");

        SP.resource("UUID").inModel(m)
                .addProperty(RDF.type, SPINMAP.TargetFunction)
                //.addProperty(RDFS.subClassOf, SPINMAP.TargetFunctions)
                .addProperty(RDFS.comment, "Also, it can be used as target function to produce named resources with autogenerated iri " +
                        "(example: <urn:uuid:6269237f-3586-4db0-8a49-22471d4289c3>).");

        m.write(System.out, "ttl");

    }
}
