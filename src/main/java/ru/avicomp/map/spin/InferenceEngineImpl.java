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

package ru.avicomp.map.spin;

import org.apache.jena.enhanced.BuiltinPersonalities;
import org.apache.jena.graph.FrontsNode;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.GraphEventManager;
import org.apache.jena.graph.Node;
import org.apache.jena.rdf.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.topbraid.spin.util.CommandWrapper;
import org.topbraid.spin.util.QueryWrapper;
import org.topbraid.spin.vocabulary.SPIN;
import org.topbraid.spin.vocabulary.SPINMAP;
import ru.avicomp.map.MapJenaException;
import ru.avicomp.map.MapManager;
import ru.avicomp.map.MapModel;
import ru.avicomp.map.spin.vocabulary.AVC;
import ru.avicomp.map.utils.GraphLogListener;
import ru.avicomp.ontapi.jena.OntModelFactory;
import ru.avicomp.ontapi.jena.UnionGraph;
import ru.avicomp.ontapi.jena.impl.UnionModel;
import ru.avicomp.ontapi.jena.model.OntCE;
import ru.avicomp.ontapi.jena.model.OntGraphModel;
import ru.avicomp.ontapi.jena.model.OntIndividual;
import ru.avicomp.ontapi.jena.utils.Graphs;
import ru.avicomp.ontapi.jena.utils.Iter;
import ru.avicomp.ontapi.jena.vocabulary.RDF;

import java.util.*;
import java.util.stream.Collectors;

/**
 * An implementation of {@link MapManager.InferenceEngine} adapted to ontology data mapping.
 * <p>
 * Created by @szuev on 19.05.2018.
 */
@SuppressWarnings("WeakerAccess")
public class InferenceEngineImpl implements MapManager.InferenceEngine {
    static {
        // Warning: Jena stupidly allows to modify global personality (org.apache.jena.enhanced.BuiltinPersonalities#model),
        // what does SPIN API, which, also, implicitly requires that patched version everywhere.
        // It may be dangerous, increases the system load and may impact other jena-based tools.
        // but I don't think there is an easy good workaround, so it's better to put up with that modifying.
        SpinModelConfig.init(BuiltinPersonalities.model);
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(InferenceEngineImpl.class);

    protected final MapManagerImpl manager;
    protected final MapInferenceHelper helper;
    protected static final Comparator<CommandWrapper> MAP_COMPARATOR = createMapComparator();
    // Assume there is Hotspot Java 6 VM (x32)
    // Then java6 (actually java8 much less, java9 even less) approximate String memory size would be: 8 * (int) ((((no chars) * 2) + 45) / 8)
    // org.apache.jena.graph.Node_Blank contains BlankNodeId which in turn contains a String (id) ~ size: 8 (header) + 8 + (string size)
    // org.apache.jena.graph.Node_URI contains label as String ~ size: 8 + (string size)
    // For uri or blank node with length 50 (owl:ObjectProperty = 46, jena blank node id = 36) chars
    // the average size of node would be ~ 160 bytes.
    // Let it be ten times more ~ 1024 byte, i.e. 1MB ~= 1000 nodes (Wow! It is very very understated. But whatever)
    // Then 50MB threshold:
    protected static final int INTERMEDIATE_STORE_THRESHOLD = 50_000;

    public InferenceEngineImpl(MapManagerImpl manager) {
        this.manager = manager;
        this.helper = new MapInferenceHelper(manager);
    }

    @Override
    public void run(MapModel mapping, Graph source, Graph target) {
        UnionModel query = assembleQueryModel(mapping);
        // re-register runtime functions
        Iter.asStream(query.getBaseModel().listResourcesWithProperty(AVC.runtime))
                .map(r -> r.inModel(query))
                .forEach(manager.arqFactory::replace);

        List<QueryWrapper> rules = getSpinMapRules(query);
        if (LOGGER.isDebugEnabled())
            rules.forEach(c -> LOGGER.debug("Rule for <{}>: '{}'", c.getStatement().getSubject(), SpinModels.toString(c)));
        // todo: what if rules is empty ?

        GraphEventManager events = target.getEventManager();
        GraphLogListener logs = new GraphLogListener(LOGGER::debug);
        OntGraphModel src = assembleDataModel(mapping, source, target);
        Model dst = ModelFactory.createModelForGraph(target);
        try {
            if (LOGGER.isDebugEnabled())
                events.register(logs);
            run(rules, src, dst);
        } finally {
            events.unregister(logs);
        }
    }

    /**
     * TODO: fix description
     * Assemblies a query model in the form of {@link UnionGraph union graph}.
     * It is just in case, the input mapping should already contain everything needed.
     *
     * @param mapping {@link MapModel} mapping model
     * @return {@link UnionModel} with SPIN personalities
     * @see SpinModelConfig#LIB_PERSONALITY
     */
    public UnionModel assembleQueryModel(MapModel mapping) {
        // spin mapping should be fully described inside the base graph.
        // also note that the return graph should not be distinct
        // (the nature of source is unknown, the distinct mode might unpredictable degrade performance and memory usage):
        UnionGraph res = new UnionGraph(mapping.asGraphModel().getBaseGraph(), null, null, false);
        // add everything from mapping:
        Graphs.flat(((UnionGraph) mapping.asGraphModel().getGraph()).getUnderlying()).forEach(res::addGraph);
        // pass prefixes:
        res.getPrefixMapping().setNsPrefixes(mapping.asGraphModel());
        // to ensure that all graphs from the library (with except of avc.*) are present (just in case) :
        Graphs.flat(manager.getMapLibraryGraph()).forEach(res::addGraph);
        return new UnionModel(res, SpinModelConfig.LIB_PERSONALITY);
    }

    /**
     * TODO: fix description
     * The mapping contains both source and target schema, but may also contain source data.
     * This method should return OWL model with schema and data and without any other additions.
     *
     * @param mapping {@link MapModel}
     * @param source  {@link Graph}
     * @param target  {@link Graph}
     * @return {@link OntGraphModel}
     */
    public OntGraphModel assembleDataModel(MapModel mapping, Graph source, Graph target) {
        Set<Graph> mappings = mapping.ontologies()
                .flatMap(o -> Graphs.flat(o.getGraph())).collect(Collectors.toSet());
        List<Graph> sources = Graphs.flat(source).collect(Collectors.toList());
        if (mappings.containsAll(sources)) { // the source contains schema
            return OntModelFactory.createModel(source, SpinModelConfig.ONT_PERSONALITY);
        }
        // Otherwise the raw no-schema data is specified -> assembly source from the given parts:
        Set<Graph> exclude = Graphs.flat(manager.getMapLibraryGraph()).collect(Collectors.toSet());
        Graphs.flat(target).forEach(exclude::add);
        List<Graph> schemas = mappings.stream().filter(x -> !exclude.contains(x)).collect(Collectors.toList());
        // the result is not distinct, since the nature of source is unknown,
        // and the distinct mode might unpredictable degrade performance and memory usage:
        UnionGraph res = new UnionGraph(sources.remove(0), null, null, false);
        sources.forEach(res::addGraph);
        schemas.forEach(res::addGraph);
        return OntModelFactory.createModel(res, SpinModelConfig.ONT_PERSONALITY);
    }

    /**
     * Runs a query collection on one model and stores the result to another.
     *
     * @param queries List of {@link QueryWrapper}s
     * @param src     {@link OntGraphModel} containing source individuals
     * @param dst     {@link Model} to write
     */
    protected void run(List<QueryWrapper> queries, OntGraphModel src, Model dst) {
        Set<Node> inMemory = new HashSet<>();
        // first process all direct individuals from the source graph:
        src.classAssertions().forEach(i -> {
            List<QueryWrapper> selected = select(queries, getClasses(i));
            Map<Resource, Set<QueryWrapper>> visited;
            process(selected, visited = new HashMap<>(), inMemory, dst, i);
            // in case no enough memory to keep temporary objects, flush them immediately:
            if (inMemory.size() > INTERMEDIATE_STORE_THRESHOLD) {
                process(queries, visited, inMemory, dst);
            }
        });
        // next iteration: flush temporary stored individuals, which appeared while the first process
        process(queries, new HashMap<>(), inMemory, dst);
    }

    /**
     * Runs a query collection for a collection of individuals (in form of regular resources),
     * stores the result to the specified model.
     *
     * @param queries     List of {@link QueryWrapper}s
     * @param processed   Map of already processed individual-queries to prevent recursion
     * @param individuals List of {@link Resource}s
     * @param target       {@link Model} to write
     */
    protected void process(List<QueryWrapper> queries,
                           Map<Resource, Set<QueryWrapper>> processed,
                           Set<Node> individuals,
                           Model target) {
        Iterator<Node> iterator = individuals.iterator();
        while (iterator.hasNext()) {
            Resource i = target.asRDFNode(iterator.next()).asResource();
            List<QueryWrapper> selected = select(queries, getClasses(i));
            process(selected, processed, individuals, target, i);
            iterator.remove();
        }
    }

    /**
     * Processes inference on individual.
     *
     * @param queries    List of {@link QueryWrapper}, queries to be run on specified individual
     * @param processed  Map of already processed individual-queries to prevent recursion
     * @param store      Set of {@link Node}s, the collection of result individuals to process in next step
     * @param target     Model to write inference result
     * @param individual {@link Resource} individual to process
     */
    protected void process(List<QueryWrapper> queries,
                           Map<Resource, Set<QueryWrapper>> processed,
                           Set<Node> store,
                           Model target,
                           Resource individual) {
        queries.forEach(c -> {
            if (processed.computeIfAbsent(individual, i -> new HashSet<>()).contains(c)) {
                LOGGER.warn("The query '{}' has been already processed for individual {}", SpinModels.toString(c), individual);
                return;
            }
            LOGGER.debug("RUN: {} ::: '{}'", individual, SpinModels.toString(c));
            Model res = helper.runQueryOnInstance(c, individual);
            processed.get(individual).add(c);
            if (res.isEmpty()) {
                return;
            }
            target.add(res);
            res.listSubjectsWithProperty(RDF.type).mapWith(FrontsNode::asNode).forEachRemaining(store::add);
        });
    }

    /**
     * Gets all object resources from {@code rdf:type} statement for a specified subject in the model.
     *
     * @param i {@link Resource}, individual
     * @return Set of {@link Resource}, classes
     */
    private static Set<Resource> getClasses(Resource i) {
        return i.listProperties(RDF.type)
                .mapWith(Statement::getObject)
                .filterKeep(RDFNode::isResource)
                .mapWith(RDFNode::asResource)
                .toSet();
    }

    /**
     * Gets all types (classes) for an individual, taken into account class hierarchy.
     * TODO: move to ONT-API?
     *
     * @param individual {@link OntIndividual}
     * @return Set of {@link OntCE class expressions}
     */
    public static Set<OntCE> getClasses(OntIndividual individual) {
        Set<OntCE> res = new HashSet<>();
        individual.classes().forEach(c -> collectSuperClasses(c, res));
        return res;
    }

    private static void collectSuperClasses(OntCE ce, Set<OntCE> res) {
        if (!res.add(ce)) return;
        ce.subClassOf().forEach(c -> collectSuperClasses(c, res));
    }

    /**
     * Lists all valid spin map rules ({@code spinmap:rule}).
     *
     * @param model {@link UnionModel} a query model
     * @return List of {@link QueryWrapper}s
     */
    protected List<QueryWrapper> getSpinMapRules(UnionModel model) {
        return Iter.asStream(model.getBaseGraph().find(Node.ANY, SPINMAP.rule.asNode(), Node.ANY))
                .flatMap(t -> helper.listCommands(t, model, true, false))
                .filter(QueryWrapper.class::isInstance)
                .map(QueryWrapper.class::cast)
                .sorted(MAP_COMPARATOR::compare)
                .collect(Collectors.toList());
    }

    /**
     * Selects those queries whose source classes are in the the given list.
     * Auxiliary method.
     *
     * @param all     List of {@link QueryWrapper}s
     * @param classes List of class expressions
     * @return List of {@link QueryWrapper}
     */
    private static List<QueryWrapper> select(List<QueryWrapper> all, Set<? extends Resource> classes) {
        return all.stream()
                .filter(c -> classes.contains(c.getStatement().getSubject()))
                .collect(Collectors.toList());
    }

    /**
     * Creates a rule ({@code spin:rule}) comparator which sorts queries by contexts and puts declaration map rules
     * (i.e. {@code spinmap:rule} with {@code spinmap:targetPredicate1 rdf:type}) first.
     *
     * @return {@link Comparator} comparator for {@link CommandWrapper}s
     */
    public static Comparator<CommandWrapper> createMapComparator() {
        Comparator<Resource> mapRuleComparator = Comparator.comparing((Resource r) -> SpinModels.context(r)
                .map(String::valueOf).orElse("Unknown"))
                .thenComparing(Comparator.comparing(SpinModels::isDeclarationMapping).reversed());
        Comparator<CommandWrapper> res = (left, right) -> {
            Optional<Resource> r1 = SpinModels.rule(left);
            Optional<Resource> r2 = SpinModels.rule(right);
            return r1.isPresent() && r2.isPresent() ? mapRuleComparator.compare(r1.get(), r2.get()) : 10;
        };
        return res.thenComparing(SpinModels::toString);
    }

    /**
     * Customized spin-inference helper.
     * Created by @szuev on 27.05.2018.
     */
    public static class MapInferenceHelper extends SPINInferenceHelper {

        public MapInferenceHelper(MapManagerImpl manager) {
            super(manager.arqFactory);
        }

        public MapARQFactory factory() {
            return (MapARQFactory) arqFactory;
        }

        /**
         * Runs a given Jena Query on a given individual and returns the inferred triples as a Model.
         * <p>
         * There is a difference with SPIN-API Inferences implementation:
         * in additional to passing {@code ?this} to top-level query binding (mapping construct)
         * there is also a ONT-MAP workaround solution to place it deep in all sub-queries, which are called by specified construct.
         * Handling {@code ?this} only by top-level mapping is definitely leak of SPIN-API functionality,
         * which severely limits the space of usage opportunities.
         * But it seems that Topbraid Composer also (checked version 5.5.1) has some magic solution for that leak in its deeps,
         * maybe similar to ours:
         * testing shows that sub-queries which handled by {@code spin:eval} may accept {@code ?this} but only  in some limited conditions,
         * e.g. for original {@code spinmap:Mapping-1-1}, which has no been cloned with changing namespace to local mapping model.
         *
         * @param query    {@link QueryWrapper}
         * @param instance {@link Resource}
         * @return {@link Model} new triples
         * @throws MapJenaException in case exception occurred while inference
         * @see SPINInferenceHelper#runQueryOnInstance(QueryWrapper, Resource)
         * @see AVC#currentIndividual
         * @see AVC#MagicFunctions
         */
        @Override
        public Model runQueryOnInstance(QueryWrapper query, Resource instance) {
            Model model = MapJenaException.notNull(query.getSPINQuery().getModel(), "Unattached query: " + query);
            Resource get = AVC.currentIndividual.inModel(model);
            Map<Statement, Statement> vars = getThisVarReplacement(get, instance);
            try {
                vars.forEach((a, b) -> model.add(b).remove(a));
                if (!vars.isEmpty()) {
                    factory().replace(get);
                }
                try {
                    return super.runQueryOnInstance(query, instance);
                } catch (RuntimeException ex) {
                    throw Exceptions.INFERENCE_FAIL.create()
                            .add(Exceptions.Key.QUERY, SpinModels.toString(query))
                            .add(Exceptions.Key.INSTANCE, instance.toString())
                            .build(ex);
                }
            } finally {
                vars.forEach((a, b) -> model.add(a).remove(b));
            }
        }

        private static Map<Statement, Statement> getThisVarReplacement(Resource function, Resource instance) {
            Model m = function.getModel();
            return SpinModels.getFunctionBody(m, function).stream()
                    .filter(s -> Objects.equals(s.getObject(), SPIN._this))
                    .collect(Collectors.toMap(x -> x, x -> m.createStatement(x.getSubject(), x.getPredicate(), instance)));
        }

    }
}
