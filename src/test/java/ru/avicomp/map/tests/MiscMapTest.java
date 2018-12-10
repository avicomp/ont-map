/*
 * This file is part of the ONT MAP.
 * The contents of this file are subject to the Apache License, Version 2.0.
 * Copyright (c) 2018, Avicomp Services, AO
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ru.avicomp.map.tests;

import org.apache.jena.shared.PrefixMapping;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.topbraid.spin.vocabulary.SP;
import ru.avicomp.map.*;
import ru.avicomp.map.spin.vocabulary.AVC;
import ru.avicomp.map.spin.vocabulary.SPIF;
import ru.avicomp.map.spin.vocabulary.SPINMAPL;
import ru.avicomp.map.utils.TestUtils;
import ru.avicomp.ontapi.jena.OntModelFactory;
import ru.avicomp.ontapi.jena.model.OntClass;
import ru.avicomp.ontapi.jena.model.OntGraphModel;
import ru.avicomp.ontapi.jena.model.OntNDP;

/**
 * Created by @ssz on 01.12.2018.
 */
public class MiscMapTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(MiscMapTest.class);

    @Test
    public void testInferenceOnSimpleMappingWithUUIDAndSubClasses() {
        // Assembly source:
        OntGraphModel s = OntModelFactory.createModel();
        s.setNsPrefixes(OntModelFactory.STANDARD);
        s.setID("source");
        OntClass A = s.createOntEntity(OntClass.class, "A");
        OntClass B = s.createOntEntity(OntClass.class, "B");
        B.addSubClassOf(A);
        OntNDP p1 = s.createOntEntity(OntNDP.class, "d1");
        p1.addDomain(A);
        OntNDP p2 = s.createOntEntity(OntNDP.class, "d2");
        p2.addDomain(B);
        B.createIndividual("I").addProperty(p1, "v1").addProperty(p2, "v2");
        TestUtils.debug(s);

        // Assembly target:
        OntGraphModel t = OntModelFactory.createModel();
        t.setNsPrefixes(OntModelFactory.STANDARD);
        t.setID("target");
        OntClass C = t.createOntEntity(OntClass.class, "C");
        OntNDP p = t.createOntEntity(OntNDP.class, "p");
        p.addDomain(C);
        TestUtils.debug(t);

        // Assembly mapping:
        MapManager manager = Managers.createMapManager();
        MapFunction uuid = manager.getFunction(AVC.UUID);
        MapFunction concat = manager.getFunction(SPINMAPL.concatWithSeparator);
        MapModel map = manager.createMapModel();
        MapContext c = map.createContext(B, C, uuid.create().build());
        c.addPropertyBridge(concat.create()
                .addProperty(SP.arg1, p1)
                .addProperty(SP.arg2, p2)
                .addLiteral(SPINMAPL.separator, ", ")
                .build(), p);
        TestUtils.debug(map);

        // Run inference:
        manager.getInferenceEngine(map).run(s, t);

        // Check results:
        TestUtils.debug(t);
        Assert.assertEquals(1, t.listNamedIndividuals()
                .peek(i -> LOGGER.debug("New target individual: {}", i)).count());
    }

    @Test
    public void testInferenceOnNestedFuncMappingWithAlternativeTargetFunction() {
        NestedFuncMapTest t = new NestedFuncMapTest();
        OntGraphModel src = t.assembleSource();
        TestUtils.debug(src);

        OntGraphModel dst = t.assembleTarget();
        TestUtils.debug(dst);

        MapManager manager = t.manager();
        PrefixMapping pm = manager.prefixes();
        OntClass dstClass = TestUtils.findOntEntity(dst, OntClass.class, "TargetClass1");
        MapModel mapping = t.createMapping(manager, src, dst, () ->
                manager.getFunction(AVC.IRI).create()
                        .addFunction(SP.arg1, manager.getFunction(SPINMAPL.concatWithSeparator).create()
                                .addLiteral(SPINMAPL.separator, "")
                                .addFunction(SP.arg1, manager.getFunction(pm.expandPrefix("afn:namespace")).create()
                                        .addClass(SP.arg1, dstClass).build())
                                .addFunction(SP.arg2, manager.getFunction(pm.expandPrefix("afn:localname")).create()
                                        .addFunction(SP.arg1, manager.getFunction(AVC.currentIndividual).create())))
                        .build());
        TestUtils.debug(mapping);

        LOGGER.info("Run inference.");
        manager.getInferenceEngine(mapping).run(src, dst);
        TestUtils.debug(dst);

        LOGGER.info("Validate.");
        t.validateAfterInference(src, dst);
    }

    @Test
    public void testInferenceOnMappingWithVarargBuildStringFunction() {
        OntGraphModel src = new SplitMapTest().assembleSource();
        OntGraphModel dst = new ConditionalMapTest().assembleTarget();
        TestUtils.debug(src);
        TestUtils.debug(dst);
        Assert.assertEquals(0, dst.classAssertions().count());

        OntClass srcPerson = TestUtils.findOntEntity(src, OntClass.class, "Person");
        OntNDP firstName = TestUtils.findOntEntity(src, OntNDP.class, "first-name");
        OntNDP secondName = TestUtils.findOntEntity(src, OntNDP.class, "second-name");
        OntNDP middleName = TestUtils.findOntEntity(src, OntNDP.class, "middle-name");
        OntClass dstClass = TestUtils.findOntEntity(dst, OntClass.class, "User");
        OntNDP dstName = TestUtils.findOntEntity(dst, OntNDP.class, "user-name");

        String ns = "http://ex.com#";
        MapManager m = Managers.createMapManager();
        MapModel map = m.createMapModel();
        map.createContext(srcPerson, dstClass).addClassBridge(m.getFunction(SPINMAPL.buildURI3).create()
                .addProperty(SP.arg1, firstName)
                .addProperty(SP.arg2, middleName)
                .addProperty(SP.arg3, secondName)
                .addLiteral(SPINMAPL.template, ns + "{?1}-{?2}-{?3}").build())
                .addPropertyBridge(m.getFunction(SPIF.buildString).create()
                        .addProperty(AVC.vararg, firstName)
                        .addProperty(AVC.vararg, middleName)
                        .addProperty(AVC.vararg, secondName)
                        .addLiteral(SP.arg1, "{?3}, {?1} {?2}").build(), dstName);
        TestUtils.debug(map);
        map.runInference(src.getBaseGraph(), dst.getBaseGraph());
        TestUtils.debug(dst);
        Assert.assertEquals(2, dst.classAssertions().count());
        Assert.assertEquals(2, dst.listNamedIndividuals().peek(i -> {
            Assert.assertEquals(ns, i.getNameSpace());
            String[] names = i.getLocalName().split("-");
            Assert.assertEquals(3, names.length);
            String n = i.getRequiredProperty(dstName).getString();
            Assert.assertEquals(names[2] + ", " + names[0] + " " + names[1], n);
        }).count());
    }
}