/*
 * This file is part of the ONT MAP.
 * The contents of this file are subject to the Apache License, Version 2.0.
 * Copyright (c) 2019, Avicomp Services, AO
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

package ru.avicomp.map.tests.maps;

import org.apache.jena.vocabulary.RDF;
import org.junit.Assert;
import org.junit.Test;
import org.topbraid.spin.vocabulary.SPINMAP;
import ru.avicomp.map.MapContext;
import ru.avicomp.map.MapFunction;
import ru.avicomp.map.MapManager;
import ru.avicomp.map.MapModel;
import ru.avicomp.map.spin.vocabulary.AVC;
import ru.avicomp.map.utils.TestUtils;
import ru.avicomp.ontapi.jena.model.OntClass;
import ru.avicomp.ontapi.jena.model.OntGraphModel;
import ru.avicomp.ontapi.jena.model.OntStatement;
import ru.avicomp.ontapi.jena.utils.Models;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by @szuev on 09.04.2018.
 */
public class UUIDMapTest extends MapTestData1 {

    @Test
    @Override
    public void testInference() {
        OntGraphModel src = assembleSource();
        TestUtils.debug(src);
        OntGraphModel dst = assembleTarget();
        TestUtils.debug(dst);

        OntClass dstClass = dst.classes().findFirst().orElseThrow(AssertionError::new);

        MapManager manager = manager();
        MapModel mapping = assembleMapping(manager, src, dst);
        TestUtils.debug(mapping);

        manager.getInferenceEngine(mapping).run(src, dst);
        TestUtils.debug(dst);
        Assert.assertEquals(4, dst.individuals().count());

        manager.getInferenceEngine(mapping).run(src, dst);
        Assert.assertEquals(4, dst.individuals().count());
        Assert.assertEquals(4, dstClass.individuals().count());
    }

    @Override
    public MapModel assembleMapping(MapManager manager, OntGraphModel src, OntGraphModel dst) {
        OntClass srcClass = TestUtils.findOntEntity(src, OntClass.class, "SourceClass1");
        OntClass dstClass = TestUtils.findOntEntity(dst, OntClass.class, "TargetClass1");

        MapFunction func = manager.getFunction(AVC.UUID.getURI());
        MapFunction.Call targetFunction = func.create().build();
        MapModel res = createMappingModel(manager, "To test custom no-arg avc:UUID.\n" +
                "Please note: custom functions which is generated by API does not require additional imports.");
        res.createContext(srcClass, dstClass).addClassBridge(targetFunction);

        Assert.assertEquals(1, res.contexts().count());
        Assert.assertEquals(2, res.ontologies().count());
        MapContext c = res.contexts().findFirst().orElseThrow(AssertionError::new);
        Assert.assertEquals(srcClass, c.getSource());
        Assert.assertEquals(dstClass, c.getTarget());
        // validate the graph has function body (svc:UUID) inside:
        List<OntStatement> statements = ((OntGraphModel) res).statements(AVC.UUID, RDF.type, SPINMAP.TargetFunction)
                .filter(OntStatement::isLocal)
                .collect(Collectors.toList());
        Assert.assertEquals(1, statements.size());
        Assert.assertEquals(56, Models.getAssociatedStatements(statements.get(0).getSubject()).size());
        return res;
    }


}