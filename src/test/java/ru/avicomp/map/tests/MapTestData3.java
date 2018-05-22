package ru.avicomp.map.tests;

import org.apache.jena.vocabulary.XSD;
import ru.avicomp.ontapi.jena.model.*;

/**
 * Created by @szuev on 10.05.2018.
 */
abstract class MapTestData3 extends AbstractMapTest {

    @Override
    public OntGraphModel assembleSource() {
        OntGraphModel m = createDataModel("people");
        String ns = m.getID().getURI() + "#";
        OntClass person = m.createOntEntity(OntClass.class, ns + "Person");
        OntDT xsdString = m.getOntEntity(OntDT.class, XSD.xstring);
        OntDT xsdBoolean = m.getOntEntity(OntDT.class, XSD.xboolean);

        OntNDP address = createDataProperty(m, "address", person, xsdString);
        OntNDP firstName = createDataProperty(m, "first-name", person, xsdString);
        OntNDP secondName = createDataProperty(m, "second-name", person, xsdString);
        OntNDP middleName = createDataProperty(m, "middle-name", person, xsdString);
        OntNDP gender = createDataProperty(m, "gender", person, xsdBoolean);

        // data:
        person.createIndividual(ns + "Person-1")
                .addAssertion(firstName, xsdString.createLiteral("Bartholomew"))
                .addAssertion(secondName, xsdString.createLiteral("Stotch"))
                .addAssertion(middleName, xsdString.createLiteral("Reuel"))
                .addAssertion(gender, xsdBoolean.createLiteral(false))
                .addAssertion(address, xsdString.createLiteral("EverGreen, 112, Springfield, Avalon, OZ"));
        person.createIndividual(ns + "Person-2")
                .addAssertion(firstName, xsdString.createLiteral("Matthew"))
                .addAssertion(secondName, xsdString.createLiteral("Scotch"))
                .addAssertion(middleName, xsdString.createLiteral("Pavlovich"))
                .addAssertion(gender, xsdBoolean.createLiteral(true))
                .addAssertion(address, xsdString.createLiteral("Oxford Rd, Manchester M13 9PL, GB"));
        //todo:
        return m;
    }

    private static OntNDP createDataProperty(OntGraphModel m, String name, OntClass domain, OntDT range) {
        OntNDP res = m.createOntEntity(OntNDP.class, m.getID().getURI() + "#" + name);
        res.addDomain(domain);
        if (range != null)
            res.addRange(range);
        return res;
    }

    @Override
    public OntGraphModel assembleTarget() {
        OntGraphModel m = createDataModel("contacts");
        String ns = m.getID().getURI() + "#";
        OntClass contact = m.createOntEntity(OntClass.class, ns + "Contact");
        OntClass address = m.createOntEntity(OntClass.class, ns + "Address");
        OntNDP fullName = createDataProperty(m, "full-name", contact, null);

        OntNOP hasAddress = m.createOntEntity(OntNOP.class, ns + "contact-address");
        hasAddress.addRange(address);
        hasAddress.addDomain(contact);

        // todo:
        return m;
    }

    @Override
    public String getDataNameSpace() {
        return getNameSpace(MapTestData3.class);
    }
}