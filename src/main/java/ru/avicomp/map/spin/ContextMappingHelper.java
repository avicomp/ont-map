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

import org.apache.jena.rdf.model.*;
import org.apache.jena.shared.JenaException;
import org.topbraid.spin.vocabulary.SP;
import org.topbraid.spin.vocabulary.SPIN;
import org.topbraid.spin.vocabulary.SPINMAP;
import ru.avicomp.map.MapJenaException;
import ru.avicomp.map.spin.vocabulary.AVC;
import ru.avicomp.ontapi.jena.model.OntCE;
import ru.avicomp.ontapi.jena.utils.Iter;
import ru.avicomp.ontapi.jena.utils.Models;
import ru.avicomp.ontapi.jena.vocabulary.RDF;

import java.util.*;
import java.util.function.IntFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Auxiliary class, a helper to process mapping template call arguments (predicates).
 * Also a holder for class-properties maps related to the specified context.
 * Just to relieve the main (context) class.
 *
 * @see TemplateBuilder
 */
@SuppressWarnings("WeakerAccess")
class ContextMappingHelper {
    private final Resource mapping;
    private final MapContextImpl context;

    private Set<? extends RDFNode> sourceClassProperties;
    private Set<? extends RDFNode> targetClassProperties;

    private ContextMappingHelper(MapContextImpl context, Resource mapping) {
        this.mapping = Objects.requireNonNull(mapping);
        this.context = Objects.requireNonNull(context);
    }

    static ContextMappingHelper create(MapContextImpl context) {
        return new ContextMappingHelper(context, context.getModel().createResource());
    }

    static void addPrimaryRule(MapContextImpl context, RDFNode filterExpression) {
        addMappingRule(create(context), context.target(), filterExpression, RDF.type);
    }

    /**
     * Adds a mapping template call to the graph as {@code spinmap:rule}.
     *
     * @param helper            {@link ContextMappingHelper}
     * @param mappingExpression resource describing mapping expression
     * @param filterExpression  resource describing filter expression
     * @param target            {@link Property}
     * @return {@link Resource}
     */
    static Resource addMappingRule(ContextMappingHelper helper,
                                   RDFNode mappingExpression,
                                   RDFNode filterExpression,
                                   Property target) {
        // todo: validate property ranges if it is possible
        MapContextImpl context = helper.context;
        Resource mapping = helper.mapping;
        MapModelImpl m = context.getModel();
        Optional<Resource> classMapRule = context.primaryRule();
        mapping.addProperty(SPINMAP.context, context)
                .addProperty(SPINMAP.targetPredicate1, target);
        List<Property> mappingPredicates = helper.addExpression(SPINMAP.expression, mappingExpression).getSources();
        List<Property> filterPredicates;
        if (filterExpression != null) {
            filterPredicates = helper.addExpression(AVC.filter, filterExpression).getSources();
        } else {
            filterPredicates = Collections.emptyList();
        }

        boolean hasDefaults = helper.hasDefaults();
        boolean hasClassMapFilter = classMapRule.map(r -> r.hasProperty(AVC.filter)).orElse(false);

        Resource template;
        int mappingSources = (int) mappingPredicates.stream().distinct().count();
        if (filterExpression == null && !hasClassMapFilter && !hasDefaults && mappingSources < 3) {
            // use standard (spinmap) mapping, which does not support filter and default values
            template = SPINMAP.Mapping(mappingSources, 1).inModel(m);
        } else {
            // use custom (avc) mapping
            template = TemplateBuilder.createMappingTemplate(m, classMapRule.isPresent(), filterPredicates, mappingPredicates);
        }
        context.getSource().addProperty(SPINMAP.rule, mapping.addProperty(RDF.type, template));
        simplify(mapping);
        return mapping;
    }

    /**
     * Simplifies mapping by replacing {@code spinmap:equals} function calls with its short form.
     */
    private static void simplify(Resource mapping) {
        Model m = mapping.getModel();
        Set<Resource> expressions = Models.listProperties(mapping)
                .filter(s -> Objects.equals(s.getObject(), SPINMAP.equals))
                .filter(s -> Objects.equals(s.getPredicate(), RDF.type))
                .map(Statement::getSubject)
                .collect(Collectors.toSet());
        expressions.forEach(expr -> {
            RDFNode arg = expr.getRequiredProperty(SP.arg1).getObject();
            Set<Statement> statements = m.listStatements(null, null, expr).toSet();
            statements.forEach(s -> {
                m.add(s.getSubject(), s.getPredicate(), arg);
                m.remove(s);
                Models.deleteAll(expr);
            });
        });
    }

    /**
     * Tries to find property in the mapping by variable (e.g. {@code spin:_arg1}).
     * Sorry for that ugly solution.
     *
     * @param mapping  {@link Resource}
     * @param var      {@link Resource}
     * @param isFilter boolean, true if it is filter in AVC template
     * @return {@link Property}
     * @throws JenaException in case no property found
     */
    static Property findProperty(Resource mapping, Resource var, boolean isFilter) throws JenaException {
        int index = Integer.parseInt(var.getLocalName().replace(SPIN._ARG, ""));
        Resource template;
        Property sourcePredicate;

        if (AVC.NS.equals((template = mapping.getPropertyResourceValue(RDF.type)).getNameSpace())) { // AVC supports filter
            String name = template.getLocalName();
            int[] array = TemplateBuilder.parsePredicatesFromTemplateName(name, isFilter);
            if (array.length < index)
                throw new MapJenaException(String.format("Unable to find predicate from mapping. " +
                                "Mapping name=%s. " +
                                "Parsed %s array=%s. " +
                                "Variable index=%d",
                        name, isFilter ? "filter" : "direct", Arrays.toString(array), index));
            sourcePredicate = SPINMAP.sourcePredicate(array[index - 1]);
        } else {
            sourcePredicate = SPINMAP.sourcePredicate(index);
        }
        Optional<Property> res = Iter.findFirst(mapping.listProperties(sourcePredicate)
                .mapWith(Statement::getObject)
                .filterKeep(o -> o.canAs(Property.class))
                .mapWith(o -> o.as(Property.class)));
        if (res.isPresent()) return res.get();
        // special case of spinmap:Mapping-0-1 and spinmap:targetResource, as a hotfix right now:
        if (mapping.hasProperty(RDF.type, SPINMAP.Mapping_0_1)) {
            Property targetPredicate = SPINMAP.targetPredicate(index);
            return mapping.getPropertyResourceValue(targetPredicate).as(Property.class);
        }
        throw new MapJenaException("Can't find property for variable " + var);
    }

    Resource getMapping() {
        return mapping;
    }

    Map<Property, Property> getSourcePredicatesMap() {
        return getMapPredicates(SPINMAP.SOURCE_PREDICATE);
    }

    Map<Property, Property> getTargetPredicatesMap() {
        return getMapPredicates(SPINMAP.TARGET_PREDICATE);
    }

    boolean hasDefaults() {
        return properties()
                .map(Statement::getPredicate)
                .map(Property::getLocalName)
                .anyMatch(s -> s.endsWith(AVC.DEFAULT_PREDICATE_SUFFIX));
    }

    /**
     * Gets and caches properties belonging to the source context class.
     *
     * @return Set of properties
     */
    Set<? extends RDFNode> getSourceClassProperties() {
        return sourceClassProperties == null ? sourceClassProperties = getClassProperties(context.getSource()) : sourceClassProperties;
    }

    /**
     * Gets and caches properties belonging to the target context class.
     *
     * @return Set of properties
     */
    Set<? extends RDFNode> getTargetClassProperties() {
        return targetClassProperties == null ? targetClassProperties = getClassProperties(context.target()) : targetClassProperties;
    }

    boolean isContextProperty(RDFNode node) {
        return node.isURIResource() && (isSourceProperty(node) || isTargetProperty(node));
    }

    boolean isSourceProperty(RDFNode property) {
        return getSourceClassProperties().contains(property);
    }

    boolean isTargetProperty(RDFNode property) {
        return getTargetClassProperties().contains(property);
    }

    private Set<Property> getClassProperties(Resource clazz) {
        return clazz.canAs(OntCE.class) ?
                context.getModel().properties(clazz.as(OntCE.class)).collect(Collectors.toSet()) :
                Collections.emptySet();
    }

    private Map<Property, Property> getMapPredicates(String prefix) {
        return properties()
                .filter(s -> s.getPredicate().getLocalName().startsWith(prefix))
                .collect(Collectors.toMap(s -> s.getObject().as(Property.class), Statement::getPredicate));
    }

    private Stream<Statement> properties() {
        return Iter.asStream(mapping.listProperties()
                .filterKeep(s -> s.getObject().isURIResource()));
    }

    /**
     * Adds an expression to mapping call and process its arguments (predicates).
     *
     * @param expressionPredicate {@link Property} predicate. e.g. {@code spinmap:expression}
     * @param expressionObject    {@link RDFNode} the expression body
     * @return {@link ExprRes} container with property collections that will be needed when building a mapping template.
     */
    ExprRes addExpression(Property expressionPredicate, RDFNode expressionObject) {
        mapping.addProperty(expressionPredicate, expressionObject);
        ExprRes res = addExpression(expressionPredicate);
        if (!res.target.isEmpty()) {
            throw new UnsupportedOperationException("TODO: expression with arguments from right side are not supported right now.");
        }
        return res;
    }

    private ExprRes addExpression(Property expressionPredicate) {
        ExprRes res = new ExprRes();
        MapModelImpl m = context.getModel();
        Map<Property, Property> sourcePredicatesMap = getSourcePredicatesMap();
        Map<Property, Property> targetPredicatesMap = getTargetPredicatesMap();
        Statement expression = mapping.getRequiredProperty(expressionPredicate);
        // properties from expression, not distinct flat list, i.e. with possible repetitions
        List<Statement> properties = Stream.concat(Stream.of(expression), Models.listProperties(expression.getObject()))
                .filter(s -> isContextProperty(s.getObject()))
                .collect(Collectors.toList());
        int variableIndex = 1;
        for (Statement s : properties) {
            Resource expr = s.getSubject();
            Property property = s.getObject().as(Property.class);
            if (!expr.hasProperty(RDF.type, AVC.asIRI)) {
                // replace argument property with variable, e.g. spin:_arg1
                Resource variable;
                if (res.replacement.containsKey(property)) {
                    variable = res.replacement.get(property);
                } else {
                    res.replacement.put(property, variable = m.getArgVariable(variableIndex++));
                }
                m.add(expr, s.getPredicate(), variable);
                m.remove(s);
            }
            // add mapping predicate
            Property predicate;
            if (isSourceProperty(property)) {
                predicate = processPredicate(property, sourcePredicatesMap, m::getSourcePredicate);
                res.sources.add(predicate);
            } else {
                predicate = processPredicate(property, targetPredicatesMap, m::getTargetPredicate);
                res.target.add(predicate);
            }
            // process default value
            if (expr.hasProperty(RDF.type, AVC.withDefault) && expr.hasProperty(SP.arg2)) {
                Literal defaultValue = expr.getProperty(SP.arg2).getObject().asLiteral();
                mapping.addProperty(m.createArgProperty(AVC.predicateDefaultValue(predicate.getLocalName()).getURI()),
                        defaultValue);
            }
        }
        return res;
    }

    private Property processPredicate(Property property, Map<Property, Property> prev, IntFunction<Property> generator) {
        Property predicate;
        if (!prev.containsKey(property)) {
            predicate = generator.apply(prev.size() + 1);
            mapping.addProperty(predicate, property);
            prev.put(property, predicate);
        } else {
            predicate = prev.get(property);
        }
        return predicate;
    }

    /**
     * Expression settings holder.
     * {@code sources} - List of mapping source predicates, e.g. {@code spinmap:sourcePredicate1}, with possible repetitions
     * {@code targets} - List of mapping target predicates, e.g. {@code spinmap:targetPredicate1}, with possible repetitions
     * {@code replacement} - Property-Variable map, where keys are existing properties either from source or target classes
     * and values - spin variables, e.g. {@code spin:_arg1}
     */
    class ExprRes {
        private final List<Property> sources = new ArrayList<>();
        private final List<Property> target = new ArrayList<>();
        private final Map<Property, Resource> replacement = new HashMap<>();

        public List<Property> getSources() {
            return Collections.unmodifiableList(sources);
        }
    }
}
