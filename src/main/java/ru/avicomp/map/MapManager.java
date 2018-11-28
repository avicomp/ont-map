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

package ru.avicomp.map;

import org.apache.jena.graph.Graph;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.shared.PrefixMapping;
import ru.avicomp.ontapi.jena.model.OntGraphModel;

import java.util.Objects;
import java.util.stream.Stream;

/**
 * A Mapping Manager.
 * This is the only place to manage everything that required to build and conduct {@link MapModel OWL2 Mapping Model}s
 * including {@link InferenceEngine map inference}, {@link MapFunction map-function}s,
 * {@link ClassPropertyMap class-property mapping}s and tools to create/delete new map-functions.
 * Please note: unlike a {@link ru.avicomp.ontapi.OntologyManager OWL Obtology Manager} this manager
 * is not a model storage, and does not responsible for models loading and saving.
 * <p>
 * Created by @szuev on 06.04.2018.
 */
public interface MapManager {

    /**
     * Returns a collection of all know prefixes from the underling library.
     * This method is just for convenience.
     *
     * @return Unmodifiable {@link PrefixMapping PrefixMapping}
     */
    PrefixMapping prefixes();

    /**
     * Lists all available functions, that can be safely used in the API.
     *
     * @return Stream of {@link MapFunction}s
     */
    Stream<MapFunction> functions();

    /**
     * Returns the primary manager graph, that is a holder for all user-defined functions.
     * It is assumed that the returning graph is unmodifiable:
     * all changes in it must occur through other methods of this interface.
     *
     * @return {@link Graph}
     * @see MapFunction#isUserDefined()
     */
    Graph getGraph();

    /**
     * Creates a fresh mapping model.
     *
     * @return {@link MapModel}
     */
    MapModel createMapModel();

    /**
     * Wraps an ontology model to the map model interface.
     * If the specified model contains custom functions inside, they will be registered in the manager.
     *
     * @param model {@link OntGraphModel}
     * @return {@link MapModel}
     * @throws MapJenaException if such wrapping is not possible
     */
    MapModel asMapModel(OntGraphModel model) throws MapJenaException;

    /**
     * Answers {@code true} if the given ontology model is also a mapping model.
     *
     * @param model {@link OntGraphModel}
     * @return boolean indicating whether the ontology contains mapping specific elements
     * and therefore is assignable to the {@link MapModel mapping} interface
     */
    boolean isMapModel(OntGraphModel model);

    /**
     * Provides a class-properties mapping.
     * <p>
     * Used directly by API to build mappings rules:
     * properties that "belong" to the context class are treated as assertions,
     * the rest of the properties are used simply as IRIs.
     * Notice that pure spin-map (an API default implementation) does not require a property to be "belonged" to a class,
     * i.e. it allows to perform mapping even a property has no domain with a class from a context.
     *
     * @param model {@link OntGraphModel OWL model}
     * @return {@link ClassPropertyMap class properties mapping object}
     */
    ClassPropertyMap getClassProperties(OntGraphModel model);

    /**
     * Gets an engine to conduct inference on top of the specified {@link MapModel Mapping Model}.
     *
     * @param mapping {@link MapModel}, not {@code null}
     * @return {@link InferenceEngine}, not {@code null}
     * @throws MapJenaException in case the mapping is not ready for inference
     */
    InferenceEngine getInferenceEngine(MapModel mapping) throws MapJenaException;

    /**
     * Gets a MapFunction by IRI Resource.
     *
     * @param resource {@link Resource}
     * @return {@link MapFunction}
     * @throws MapJenaException if no function found
     */
    default MapFunction getFunction(Resource resource) throws MapJenaException {
        if (!MapJenaException.notNull(resource, "Null function resource").isURIResource())
            throw new MapJenaException.IllegalArgument("Not an iri");
        return getFunction(resource.getURI());
    }

    /**
     * Gets function by given name (an IRI in our single implementation).
     * The implementation is free to override it in order to provide access to hidden functions,
     * i.e. those which are not listed by {@link #functions()} method.
     *
     * @param name String, not null
     * @return {@link MapFunction}
     * @throws MapJenaException if no function found
     */
    default MapFunction getFunction(String name) throws MapJenaException {
        return functions()
                .filter(f -> Objects.equals(name, f.name()))
                .findFirst()
                .orElseThrow(() -> new MapJenaException("Function " + name + " not found."));
    }

    /**
     * Creates a fresh mapping model with a given uri.
     * Just for convenience, bearing in mind that Composer does not understand anonymous ontologies (checked ver 5.5.2).
     *
     * @param uri String
     * @return {@link MapModel}
     */
    default MapModel createMapModel(String uri) {
        MapModel res = createMapModel();
        res.setID(uri);
        return res;
    }

    /**
     * An inference engine,
     * that is a service-model to conduct transferring and transforming data
     * from source to target according to the encapsulated mapping-instructions.
     * <p>
     * In our (currently single) implementation it is a SPIN-based inference engine.
     *
     * @see #getInferenceEngine(MapModel)
     */
    interface InferenceEngine {

        /**
         * Performs an inference operation over the {@code source} data graph
         * putting the result into the {@code target} graph.
         * The term 'inference' here means sequential processing
         * of the mapping instructions, that are internal to the engine, over the data from the {@code source} graph.
         * <p>
         * Both the {@code source} and the {@code target} graphs may be raw
         * (i.e. only data without schema) or full (i.e. data plus schema).
         * If no mapping rules, that are suitable for the specified {@code source} data, are found,
         * then {@link MapJenaException Map Exception} is expected.
         *
         * @param source a graph with data to infer, not {@code null}
         * @param target a graph to write mapping results, not {@code null}
         * @throws MapJenaException in case if something goes wrong
         */
        void run(Graph source, Graph target) throws MapJenaException;

        /**
         * Performs an inference operation over the {@code source} data model
         * putting the result into the {@code target} model.
         *
         * @param source a data {@link Model} to infer, not {@code null}
         * @param target a {@link Model} to write mapping inference results, not {@code null}
         * @throws MapJenaException some error occurs during inference
         * @see #run(Graph, Graph)
         */
        default void run(Model source, Model target) throws MapJenaException {
            run(source.getGraph(), target.getGraph());
        }
    }

}
