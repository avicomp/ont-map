package ru.avicomp.map.spin;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDFS;
import org.topbraid.spin.arq.ARQ2SPIN;
import org.topbraid.spin.vocabulary.SPIN;
import org.topbraid.spin.vocabulary.SPINMAP;
import org.topbraid.spin.vocabulary.SPL;
import ru.avicomp.map.MapJenaException;
import ru.avicomp.map.spin.vocabulary.AVC;
import ru.avicomp.ontapi.jena.utils.Models;
import ru.avicomp.ontapi.jena.vocabulary.RDF;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Auxiliary class-helper to build spin construct mapping template optimised to ONT-MAP API logic.
 * In additional to common capabilities the result mapping template must be able to accept and evaluate filter expression and default values.
 * <p>
 * Notice that this functionality is absent in the standard spin-library supply:
 * a spinmap conditional mapping (see {@code spinmap:Conditional-Mapping-1-1}) can only accept ASK query, not abstract expression;
 * no default values are supported by others standard mappings (e.g. {@code spinmap:Mapping-2-1}),
 * as a result all mappings are skipped in case there is no corresponding data assertion on individual.
 * <p>
 * Created by @szuev on 05.05.2018.
 */
@SuppressWarnings({"WeakerAccess", "UnusedReturnValue"})
public class MappingBuilder {
    // query variables:
    private Set<String> sourcePredicates = new LinkedHashSet<>();
    private String targetPredicate;
    private Map<String, String> defaultValues = new HashMap<>();
    private List<String> filterExpressionArguments = new ArrayList<>();
    private List<String> mappingExpressionArguments = new ArrayList<>();
    private String mappingExpression;
    private String filterExpression;

    /**
     * Finds or creates an universal custom mapping template {@code avc:Mapping--...--1} with possibility to filter and set default values.
     * The result template goes directly to the specified model graph.
     *
     * @param model            {@link MapModelImpl}
     * @param filterPredicates List of predicates (i.e. {@code spinmap:sourcePredicate$i}), which are used while filtering
     * @param sourcePredicates List of predicates (i.e. {@code spinmap:sourcePredicate$i}), which are used while mapping
     * @return {@link Resource} a fresh or found mapping template resource in model
     * @throws MapJenaException if something goes wrong
     */
    public static Resource createMappingTemplate(MapModelImpl model, List<Property> filterPredicates, List<Property> sourcePredicates)
            throws MapJenaException {
        String filters = toPredicateString(filterPredicates);
        String sources = toPredicateString(sourcePredicates);
        Resource res = AVC.Mapping(filters, sources).inModel(model);
        if (model.contains(res, RDF.type, SPIN.ConstructTemplate)) {
            return res;
        }
        MappingBuilder query = new MappingBuilder()
                .addMappingExpression(SPINMAP.expression.getLocalName())
                .addTargetPredicate(SPINMAP.targetPredicate1.getLocalName());
        // mandatory mapping expression argument:
        res.addProperty(SPIN.constraint, model.createResource()
                .addProperty(RDF.type, SPL.Argument)
                .addProperty(SPL.predicate, SPINMAP.expression));
        // optional filter expression argument:
        query.addFilterExpression(AVC.filter.getLocalName());
        res.addProperty(SPIN.constraint, model.createResource()
                .addProperty(RDF.type, SPL.Argument)
                .addProperty(SPL.predicate, AVC.filter)
                .addProperty(SPL.optional, Models.TRUE));

        // process all predicates constraints:
        Stream.of(filterPredicates, sourcePredicates)
                .flatMap(Collection::stream)
                .sorted(Comparator.comparing(Resource::getURI))
                .distinct()
                .forEach(predicate -> {
                    Resource constraint = model.createResource();
                    res.addProperty(SPIN.constraint, constraint
                            .addProperty(RDF.type, SPL.Argument)
                            .addProperty(SPL.valueType, RDF.Property)
                            .addProperty(SPL.predicate, predicate));
                    if (sourcePredicates.contains(predicate)) {
                        query.addMappingArgument(predicate.getLocalName());
                    }
                    if (filterPredicates.contains(predicate)) {
                        query.addFilterArgument(predicate.getLocalName());
                        // filter predicate is optional:
                        if (!sourcePredicates.contains(predicate)) {
                            constraint.addProperty(SPL.optional, Models.TRUE);
                        }
                    } else {
                        // default value is optional:
                        Property defaultValue = model.createArgProperty(AVC.sourceDefaultValue(predicate.getLocalName()).getURI());
                        res.addProperty(SPIN.constraint, model.createResource()
                                .addProperty(RDF.type, SPL.Argument)
                                .addProperty(SPL.predicate, defaultValue)
                                .addProperty(SPL.valueType, RDFS.Literal)
                                .addProperty(SPL.optional, Models.TRUE));
                        query.addSourceDefaultValue(predicate.getLocalName(), defaultValue.getLocalName());
                    }
                    query.addSourcePredicate(predicate.getLocalName());
                });
        Model m = SpinModelConfig.createSpinModel(model.getGraph());
        res.addProperty(RDF.type, SPIN.ConstructTemplate)
                .addProperty(RDFS.subClassOf, SPINMAP.Mapping_1)
                .addProperty(SPIN.body, ARQ2SPIN.parseQuery(query.build(), m));
        return res;
    }

    public static String toPredicateString(Collection<Property> properties) {
        return properties.stream().map(p -> p.getLocalName().replace(SPINMAP.SOURCE_PREDICATE_PREFIX, "")).collect(Collectors.joining("-"));
    }

    public static String asOptional(String expr) {
        return String.format("\tOPTIONAL {\n\t\t%s\n\t} .", expr);
    }

    public static String makeEvalCall(String expressionVariable, List<String> argumentVariables) {
        if (argumentVariables.isEmpty()) {
            return "spin:eval(?" + expressionVariable + ", sp:arg1, ?this)";
        }
        return Stream.concat(Stream.of(expressionVariable), IntStream.rangeClosed(1, argumentVariables.size())
                .mapToObj(i -> "sp:arg" + i + ", ?" + argumentVariables.get(i - 1)))
                .collect(Collectors.joining(", ", "spin:eval(?", ")"));
    }

    public static void main(String... args) { // todo: test
        String r = new MappingBuilder()
                .addMappingExpression("expression")
                .addFilterExpression("filter")
                .addSourceDefaultValue("sourcePredicate1", "defaultValue1")
                .addSourceDefaultValue("sourcePredicate2", "defaultValue2")
                .addSourcePredicate("sourcePredicate3")
                .addMappingArgument("sourcePredicate1")
                .addMappingArgument("sourcePredicate2")
                .addFilterArgument("sourcePredicate3")
                .addTargetPredicate("targetPredicate1")
                .build();
        System.out.println(r);
    }

    public MappingBuilder addSourceDefaultValue(String sourcePredicateVariable, String defaultValueVariable) {
        addSourcePredicate(sourcePredicateVariable);
        defaultValues.put(sourcePredicateVariable, defaultValueVariable);
        return this;
    }

    public MappingBuilder addSourcePredicate(String sourcePredicateVariable) {
        sourcePredicates.add(sourcePredicateVariable);
        return this;
    }

    public MappingBuilder addTargetPredicate(String targetPredicateVariable) {
        this.targetPredicate = targetPredicateVariable;
        return this;
    }

    public MappingBuilder addFilterArgument(String filterPredicateVariable) {
        filterExpressionArguments.add(filterPredicateVariable);
        return this;
    }

    public MappingBuilder addMappingArgument(String mapPredicateVariable) {
        mappingExpressionArguments.add(mapPredicateVariable);
        return this;
    }

    public MappingBuilder addMappingExpression(String expression) {
        this.mappingExpression = expression;
        return this;
    }

    public MappingBuilder addFilterExpression(String expression) {
        this.filterExpression = expression;
        return this;
    }

    /**
     * Builds a construct SPARQL query.
     * Example:
     * <pre>{@code CONSTRUCT {
     * 	?target ?targetPredicate1 ?resValue .
     * }
     * WHERE {
     * 	OPTIONAL {
     * 		?this ?sourcePredicate1 ?value1 .
     * 	} .
     * 	OPTIONAL {
     * 		BIND (?defaultValue1 AS ?value1) .
     * 	} .
     * 	OPTIONAL {
     * 		?this ?sourcePredicate2 ?value2 .
     * 	} .
     * 	OPTIONAL {
     * 		BIND (?defaultValue2 AS ?value2) .
     * 	} .
     * 	OPTIONAL {
     * 		?this ?sourcePredicate3 ?value3 .
     * 	} .
     * 	BIND (spin:eval(?expression, sp:arg1, ?value1, sp:arg2, ?value2) AS ?resValue) .
     * 	BIND (spinmap:targetResource(?this, ?context) AS ?target) .
     * 	FILTER (!bound(?filter) || spin:eval(?filter, sp:arg1, ?value3)) .
     * }}</pre>
     *
     * @return String
     */
    public String build() {
        Objects.requireNonNull(mappingExpression, "Null expression variable name");
        Objects.requireNonNull(targetPredicate, "Null target predicate variable name");
        String resValue = "resValue";
        String assertionValuePrefix = "value";
        StringBuilder query = new StringBuilder("CONSTRUCT {\n\t")
                .append("?target ?").append(targetPredicate).append(" ?").append(resValue)
                .append(" .\n}\nWHERE {\n");

        int varIndex = 1;
        List<String> mappingVariables = new ArrayList<>();
        List<String> filterVariables = new ArrayList<>();
        for (String p : sourcePredicates) {
            String v = assertionValuePrefix + varIndex++;
            String t = "?this ?" + p + " ?" + v + " .";
            query.append(asOptional(t)).append("\n");
            String d = defaultValues.get(p);
            if (d != null) {
                query.append(asOptional("BIND (?" + d + " AS ?" + v + ") .")).append("\n");
            }
            if (mappingExpressionArguments.contains(p)) {
                mappingVariables.add(v);
            }
            if (filterExpressionArguments.contains(p)) {
                filterVariables.add(v);
            }
        }
        query.append("\tBIND (").append(makeEvalCall(mappingExpression, mappingVariables))
                .append(" AS ?")
                .append(resValue).append(") .\n\tBIND (spinmap:targetResource(?this, ?context) AS ?target) .");
        if (filterExpression != null) {
            query.append("\n\tFILTER ")
                    .append("(!bound(?").append(filterExpression).append(") || ")
                    .append(makeEvalCall(filterExpression, filterVariables)).append(") .");
        }
        query.append("\n}");
        return query.toString();
    }

}