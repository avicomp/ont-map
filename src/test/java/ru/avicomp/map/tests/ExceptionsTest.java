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
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.topbraid.spin.vocabulary.SP;
import ru.avicomp.map.*;
import ru.avicomp.map.spin.Exceptions;
import ru.avicomp.map.spin.vocabulary.FN;
import ru.avicomp.map.spin.vocabulary.SPINMAPL;
import ru.avicomp.map.utils.TestUtils;
import ru.avicomp.ontapi.jena.OntModelFactory;
import ru.avicomp.ontapi.jena.model.OntClass;
import ru.avicomp.ontapi.jena.model.OntGraphModel;
import ru.avicomp.ontapi.jena.model.OntNDP;

import java.util.Arrays;

import static ru.avicomp.map.spin.Exceptions.CONTEXT_REQUIRE_TARGET_FUNCTION;
import static ru.avicomp.map.spin.Exceptions.Key;

/**
 * Created by @szuev on 18.04.2018.
 * TODO: add tests for {@link MapModel#validate(MapFunction.Call)} (issue https://github.com/avicomp/ont-map/issues/7)
 */
public class ExceptionsTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExceptionsTest.class);

    private static MapManager manager;

    @BeforeClass
    public static void before() {
        manager = Managers.createMapManager();
    }

    @Test
    public void testNonTargetFuncError() {
        String uri = "ex://test";
        String ns = uri + "#";
        OntGraphModel m = OntModelFactory.createModel();
        m.setID(uri);
        OntClass src = m.createOntEntity(OntClass.class, ns + "src");
        OntClass dst = m.createOntEntity(OntClass.class, ns + "dst");
        MapModel map = manager.createMapModel();
        MapContext context = map.createContext(src, dst);
        Assert.assertNotNull(context);
        MapFunction f = manager.getFunction(manager.prefixes().expandPrefix("smf:currentUserName"));
        Assert.assertNotNull(f);
        try {
            context.addClassBridge(f.create().build());
            Assert.fail("Expression has been added successfully");
        } catch (Exceptions.SpinMapException e) {
            assertCode(e, CONTEXT_REQUIRE_TARGET_FUNCTION);
            Assert.assertEquals(f.name(), e.getString(Key.FUNCTION));
            Assert.assertEquals(src.getURI(), e.getString(Key.CONTEXT_SOURCE));
            Assert.assertEquals(dst.getURI(), e.getString(Key.CONTEXT_TARGET));
        }
    }

    @Test
    public void testBuildFunction() {
        MapFunction f = manager.getFunction(manager.prefixes().expandPrefix("spinmapl:concatWithSeparator"));
        try {
            f.create().build();
            Assert.fail("Expected error");
        } catch (MapJenaException j) {
            assertCode(j, Exceptions.FUNCTION_NO_REQUIRED_ARG);
            Assert.assertEquals(3, ((Exceptions.SpinMapException) j).getList(Key.ARG).size());
            Assert.assertEquals(f.name(), ((Exceptions.SpinMapException) j).getString(Key.FUNCTION));
        }
        try {
            f.create().add(manager.prefixes().expandPrefix("sp:arg1"), "x").build();
            Assert.fail("Expected error");
        } catch (MapJenaException j) { // no required arg
            assertCode(j, Exceptions.FUNCTION_NO_REQUIRED_ARG);
            Assert.assertEquals(2, ((Exceptions.SpinMapException) j).getList(Key.ARG).size());
            Assert.assertEquals(f.name(), ((Exceptions.SpinMapException) j).getString(Key.FUNCTION));
        }
        String p = "http://unknown-prefix.org";
        try {
            f.create().add(p, "xxx");
            Assert.fail("Expected error");
        } catch (Exceptions.SpinMapException e) { // non existent arg
            assertCode(e, Exceptions.FUNCTION_NONEXISTENT_ARGUMENT);
            Assert.assertEquals(p, e.getString(Key.ARG));
            Assert.assertEquals(f.name(), e.getString(Key.FUNCTION));
        }
    }

    @Test(expected = Exceptions.SpinMapException.class)
    public void testBuildCallNoRequiredArg() {
        manager.getFunction(SPINMAPL.buildURI1).create().build();
    }

    @Test(expected = Exceptions.SpinMapException.class)
    public void testBuildCallNonExistArg() {
        manager.getFunction(SP.resource("contains"))
                .create()
                .addLiteral(SP.arg1, "a")
                .addLiteral(SP.arg2, "b")
                .addLiteral(SPINMAPL.template, "target:xxx")
                .build();
    }

    @Test
    public void testBuildImproperMapping() {
        PrefixMapping pm = manager.prefixes();

        AbstractMapTest test = new BuildURIMapTest();
        OntGraphModel s = test.assembleSource();
        OntGraphModel t = test.assembleTarget();

        OntClass sc1 = TestUtils.findOntEntity(s, OntClass.class, "SourceClass1");
        OntNDP sp1 = TestUtils.findOntEntity(s, OntNDP.class, "sourceDataProperty1");
        OntNDP sp2 = TestUtils.findOntEntity(s, OntNDP.class, "sourceDataProperty2");
        OntClass tc1 = TestUtils.findOntEntity(t, OntClass.class, "TargetClass1");
        OntNDP tp1 = TestUtils.findOntEntity(t, OntNDP.class, "targetDataProperty2");

        MapModel m = test.createMappingModel(manager, "Test improper mappings");
        MapContext c = m.createContext(sc1, tc1);
        Assert.assertEquals(1, m.contexts().count());
        long count = m.asGraphModel().statements().count();

        MapFunction.Call mapFunction, filterFunction;

        LOGGER.debug("Class bridge: wrong filter function.");
        mapFunction = manager.getFunction(SPINMAPL.buildURI2)
                .create()
                .addProperty(SP.arg1, sp1)
                .addProperty(SP.arg2, sp2)
                .addLiteral(SPINMAPL.template, "target:xxx")
                .build();
        filterFunction = manager.getFunction(SPINMAPL.self).create().build();
        try {
            c.addClassBridge(filterFunction, mapFunction);
            Assert.fail("Class bridge is added successfully");
        } catch (MapJenaException j) {
            assertCode(j, Exceptions.CONTEXT_NOT_BOOLEAN_FILTER_FUNCTION);
        }
        Assert.assertEquals(count, m.asGraphModel().statements().count());

        LOGGER.debug("Class bridge: wrong mapping function.");
        filterFunction = manager.getFunction(pm.expandPrefix("sp:strends"))
                .create()
                .addProperty(SP.arg1, sp1)
                .addProperty(SP.arg2, sp2)
                .build();
        mapFunction = manager.getFunction(pm.expandPrefix("spl:subClassOf"))
                .create()
                .addClass(SP.arg1, sc1)
                .addClass(SP.arg2, tc1)
                .build();
        try {
            c.addClassBridge(filterFunction, mapFunction);
            Assert.fail("Class bridge is added successfully");
        } catch (MapJenaException j) {
            assertCode(j, Exceptions.CONTEXT_REQUIRE_TARGET_FUNCTION);
        }
        Assert.assertEquals(count, m.asGraphModel().statements().count());

        LOGGER.debug("Property bridge: wrong mapping function.");
        mapFunction = manager.getFunction(pm.expandPrefix("spl:subClassOf")).create()
                .addProperty(SP.arg1, sp1)
                .addClass(SP.arg2, tc1).build();
        filterFunction = manager.getFunction(pm.expandPrefix("sp:ge")).create()
                .addProperty(SP.arg1, sp2)
                .addLiteral(SP.arg2, "x").build();
        try {
            c.addPropertyBridge(filterFunction, mapFunction, tp1);
            Assert.fail("Property bridge is added successfully");
        } catch (MapJenaException j) {
            assertCode(j, Exceptions.PROPERTY_BRIDGE_WRONG_MAPPING_FUNCTION);
        }
        Assert.assertEquals(count, m.asGraphModel().statements().count());

        LOGGER.debug("Property bridge: wrong filter function.");
        filterFunction = manager.getFunction(pm.expandPrefix("spl:instanceOf")).create()
                .addClass(SP.arg1, sc1)
                .addLiteral(SP.arg2, sp1.getURI()).build();
        mapFunction = manager.getFunction(pm.expandPrefix("sp:day")).create()
                .addProperty(SP.arg1, sp2).build();
        try {
            c.addPropertyBridge(filterFunction, mapFunction, tp1);
            Assert.fail("Property bridge is added successfully");
        } catch (MapJenaException j) {
            assertCode(j, Exceptions.PROPERTY_BRIDGE_WRONG_FILTER_FUNCTION);
        }
        Assert.assertEquals(count, m.asGraphModel().statements().count());

        LOGGER.debug("Property bridge: wrong target property.");
        filterFunction = manager.getFunction(pm.expandPrefix("sp:isBlank")).create()
                .addClass(SP.arg1, sc1).build();
        mapFunction = manager.getFunction(pm.expandPrefix("sp:timezone")).create()
                .addProperty(SP.arg1, sp1).build();
        try {
            c.addPropertyBridge(filterFunction, mapFunction, sp2);
            Assert.fail("Property bridge is added successfully");
        } catch (MapJenaException j) {
            assertCode(j, Exceptions.PROPERTY_BRIDGE_WRONG_TARGET_PROPERTY);
        }
        Assert.assertEquals(count, m.asGraphModel().statements().count());
    }

    @Test(expected = Exceptions.SpinMapException.class)
    public void testInferenceFail() {
        AbstractMapTest tc = new MathOpsMapTest();
        OntGraphModel src = tc.assembleSource();
        OntGraphModel dst = tc.assembleTarget();

        OntClass srcClass = TestUtils.findOntEntity(src, OntClass.class, "SrcClass1");
        OntClass dstClass = TestUtils.findOntEntity(dst, OntClass.class, "DstClass1");
        OntNDP srcProp2 = TestUtils.findOntEntity(src, OntNDP.class, "srcDataProperty2");
        OntNDP dstProp3 = TestUtils.findOntEntity(dst, OntNDP.class, "dstDataProperty3");

        MapModel map = tc.createMappingModel(manager, "Wrong fn:format-number picture");
        MapContext c = map.createContext(srcClass, dstClass, manager.getFunction(SPINMAPL.self).create().build());
        c.addPropertyBridge(manager.getFunction(FN.format_number).create()
                .addProperty(SP.arg1, srcProp2)
                .addLiteral(SP.arg2, "9,9.000"), dstProp3);
        manager.getInferenceEngine(map).run(src, dst);
        TestUtils.debug(dst);
    }

    private static void assertCode(MapJenaException j, Exceptions code) {
        print(j);
        Exceptions.SpinMapException s = (Exceptions.SpinMapException) j;
        Assert.assertEquals(code, s.getCode());
    }

    private static void print(MapJenaException j) {
        LOGGER.debug("Exception: {}", j.getMessage());
        Arrays.stream(j.getSuppressed()).forEach(e -> LOGGER.debug("Suppressed: {}", e.getMessage()));
    }
}

