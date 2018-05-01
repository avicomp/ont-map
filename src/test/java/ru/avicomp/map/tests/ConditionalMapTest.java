package ru.avicomp.map.tests;

import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.XSD;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.topbraid.spin.vocabulary.SP;
import org.topbraid.spin.vocabulary.SPIF;
import org.topbraid.spin.vocabulary.SPINMAPL;
import ru.avicomp.map.*;
import ru.avicomp.map.utils.TestUtils;
import ru.avicomp.ontapi.jena.model.*;
import ru.avicomp.ontapi.jena.vocabulary.RDF;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * TODO: not ready
 * Created by @szuev on 26.04.2018.
 */
public class ConditionalMapTest extends SimpleMapData2 {
    private final Logger LOGGER = LoggerFactory.getLogger(ConditionalMapTest.class);

    @Test
    @Override
    public void testInference() {
        LOGGER.info("Assembly models.");
        OntGraphModel s = assembleSource();
        TestUtils.debug(s);
        OntGraphModel t = assembleTarget();
        TestUtils.debug(t);
        MapManager manager = Managers.getMapManager();
        MapModel m = assembleMapping(manager, s, t);
        TestUtils.debug(m);

        LOGGER.info("Run inference.");
        manager.getInferenceEngine().run(m, s, t);
        TestUtils.debug(t);

        Assert.assertEquals(3, t.listNamedIndividuals().count());

        LOGGER.info("Re-run inference and validate.");
        manager.getInferenceEngine().run(m, s, t);
        List<OntIndividual.Named> individuals = t.listNamedIndividuals().collect(Collectors.toList());
        Assert.assertEquals(3, individuals.size());

        OntNDP email = TestUtils.findOntEntity(t, OntNDP.class, "email");
        OntNDP phone = TestUtils.findOntEntity(t, OntNDP.class, "phone");
        OntNDP skype = TestUtils.findOntEntity(t, OntNDP.class, "skype");

        // Jane has only email as string
        OntIndividual.Named jane = TestUtils.findOntEntity(t, OntIndividual.Named.class, "res-jane-contacts");
        Assert.assertEquals(1, jane.statements().filter(st -> !Objects.equals(st.getPredicate(), RDF.type)).count());
        String janeEmail = jane.objects(email, Literal.class)
                .filter(l -> XSD.xstring.getURI().equals(l.getDatatypeURI()))
                .map(Literal::getString)
                .findFirst().orElseThrow(AssertionError::new);
        Assert.assertEquals(DATA_EMAIL_JANE, janeEmail);

        // Jhon has email and skype
        OntIndividual.Named jhon = TestUtils.findOntEntity(t, OntIndividual.Named.class, "res-jhons");
        Assert.assertEquals(2, jhon.statements().filter(st -> !Objects.equals(st.getPredicate(), RDF.type)).count());
        String jhonEmail = jhon.objects(email, Literal.class)
                .filter(l -> XSD.xstring.getURI().equals(l.getDatatypeURI()))
                .map(Literal::getString)
                .findFirst().orElseThrow(AssertionError::new);
        String jhonSkype = jhon.objects(skype, Literal.class)
                .filter(l -> XSD.xstring.getURI().equals(l.getDatatypeURI()))
                .map(Literal::getString)
                .findFirst().orElseThrow(AssertionError::new);
        Assert.assertEquals(DATA_EMAIL_JHON, jhonEmail);
        Assert.assertEquals(DATA_SKYPE_JHON, jhonSkype);

        // Bob has email and phone
        OntIndividual.Named bob = TestUtils.findOntEntity(t, OntIndividual.Named.class, "res-bobs");
        Assert.assertEquals(2, bob.statements().filter(st -> !Objects.equals(st.getPredicate(), RDF.type)).count());
        String bobEmail = bob.objects(email, Literal.class)
                .filter(l -> XSD.xstring.getURI().equals(l.getDatatypeURI()))
                .map(Literal::getString)
                .findFirst().orElseThrow(AssertionError::new);
        String bobPhone = bob.objects(phone, Literal.class)
                .filter(l -> XSD.xstring.getURI().equals(l.getDatatypeURI()))
                .map(Literal::getString)
                .findFirst().orElseThrow(AssertionError::new);
        Assert.assertEquals(DATA_EMAIL_BOB, bobEmail);
        Assert.assertEquals(DATA_PHONE_BOB, bobPhone);
    }

    @Override
    public MapModel assembleMapping(MapManager manager, OntGraphModel src, OntGraphModel dst) {
        OntClass srcClass = TestUtils.findOntEntity(src, OntClass.class, "Contact");
        OntClass dstClass = TestUtils.findOntEntity(dst, OntClass.class, "User");
        Map<OntDT, OntNDP> propsMap = src.listDatatypes()
                .map(Resource::getLocalName)
                .collect(Collectors.toMap(
                        s -> TestUtils.findOntEntity(src, OntDT.class, s),
                        s -> TestUtils.findOntEntity(dst, OntNDP.class, s)));
        OntNDP sourceProperty = TestUtils.findOntEntity(src, OntNDP.class, "info");

        MapFunction.Call targetFunctionCall = manager.getFunction(SPINMAPL.composeURI.getURI())
                .createFunctionCall()
                .add(SPINMAPL.template.getURI(), dst.getID().getURI() + "#res-{?1}")
                .build();
        MapFunction eq = manager.getFunction(SP.eq.getURI());
        MapFunction datatype = manager.getFunction(SP.resource("datatype").getURI());
        MapFunction cast = manager.getFunction(SPIF.cast.getURI());

        MapModel res = manager.createMapModel();
        res.setID(getNameSpace() + "/map")
                .addComment("Used functions: spinmapl:composeURI, sp:eq, sp:datatype, spif:cast", null);
        Context context = res.createContext(srcClass, dstClass, targetFunctionCall);
        propsMap.forEach((sourceDatatype, targetProperty) -> {
            MapFunction.Call filter = eq.createFunctionCall()
                    .add(SP.arg1.getURI(), sourceDatatype.getURI())
                    .add(SP.arg2.getURI(), datatype.createFunctionCall().add(SP.arg1.getURI(), sourceProperty.getURI())).build();
            MapFunction.Call mapping = cast.createFunctionCall()
                    .add(SP.arg1.getURI(), sourceProperty.getURI())
                    .add(SPIF.argDatatype.getURI(), XSD.xstring.getURI()).build();
            context.addPropertyBridge(filter, mapping, targetProperty);
        });
        return res;
    }


}
