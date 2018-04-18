package ru.avicomp.map.spin.impl;

import org.apache.jena.atlas.iterator.Iter;
import org.apache.jena.datatypes.RDFDatatype;
import org.apache.jena.datatypes.TypeMapper;
import org.apache.jena.enhanced.EnhGraph;
import org.apache.jena.graph.Node;
import org.apache.jena.rdf.model.*;
import org.apache.jena.rdf.model.impl.ResourceImpl;
import org.apache.jena.shared.JenaException;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.XSD;
import org.topbraid.spin.vocabulary.SPIN;
import org.topbraid.spin.vocabulary.SPINMAP;
import ru.avicomp.map.Context;
import ru.avicomp.map.MapFunction;
import ru.avicomp.map.MapJenaException;
import ru.avicomp.map.PropertyBridge;
import ru.avicomp.map.spin.MapFunctionImpl;
import ru.avicomp.map.spin.MapModelImpl;
import ru.avicomp.map.utils.Models;
import ru.avicomp.ontapi.jena.model.OntCE;
import ru.avicomp.ontapi.jena.model.OntDT;
import ru.avicomp.ontapi.jena.model.OntNAP;
import ru.avicomp.ontapi.jena.model.OntNDP;
import ru.avicomp.ontapi.jena.vocabulary.RDF;

import java.util.*;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Implementation of class context (for resource with type {@code spinmap:Context}).
 *
 * Created by @szuev on 14.04.2018.
 */
@SuppressWarnings("WeakerAccess")
public class MapContextImpl extends ResourceImpl implements Context {
    private static final Map<String, Predicate<Resource>> ARG_RESOURCE_MAPPING = Collections.unmodifiableMap(new HashMap<String, Predicate<Resource>>() {
        {
            put(RDFS.Resource.getURI(), r -> true);
            put(RDF.Property.getURI(), r -> r.canAs(OntNAP.class) || r.canAs(OntNDP.class));
            put(RDFS.Class.getURI(), r -> r.canAs(OntCE.class));
            put(RDFS.Datatype.getURI(), r -> r.canAs(OntDT.class));
        }
    });
    private final TypeMapper rdfTypes = TypeMapper.getInstance();

    public MapContextImpl(Node n, EnhGraph m) {
        super(n, m);
    }

    @Override
    public MapModelImpl getModel() {
        return (MapModelImpl) super.getModel();
    }

    @Override
    public OntCE getSource() throws JenaException {
        return getRequiredProperty(SPINMAP.sourceClass).getObject().as(OntCE.class);
    }

    @Override
    public OntCE getTarget() throws JenaException {
        return getRequiredProperty(SPINMAP.targetClass).getObject().as(OntCE.class);
    }

    @Override
    public Context addExpression(MapFunction.Call func) throws MapJenaException {
        if (!func.getFunction().isTarget()) {
            // TODO: exception mechanism
            throw new MapJenaException();
        }
        validate(func);
        Resource expr = createExpression(func);
        // collects statements for existing expression to be deleted :
        List<Statement> prev = getModel().statements(this, SPINMAP.target, null)
                .map(Statement::getObject)
                .filter(RDFNode::isResource)
                .map(RDFNode::asResource)
                .map(Models::getAssociatedStatements)
                .flatMap(Collection::stream)
                .collect(Collectors.toList());

        addProperty(SPINMAP.target, expr);
        addMapping(getTarget(), Collections.emptyList(), Collections.singletonList(RDF.type));
        getModel().remove(prev);
        return this;
    }

    @Override
    public MapFunction.Call getExpression() {
        // todo:
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public PropertyBridge addPropertyBridge(MapFunction.Call func, Property target) throws MapJenaException {
        Predicate<Resource> isProperty = ARG_RESOURCE_MAPPING.get(RDF.Property.getURI());
        if (!isProperty.test(target)) {
            throw new MapJenaException("TODO");
        }
        validate(func);
        Resource expr = createExpression(func);
        List<Property> props = Iter.asStream(expr.listProperties()).map(Statement::getObject)
                .filter(RDFNode::isURIResource)
                .filter(p -> isProperty.test(p.asResource()))
                .map(p -> p.as(Property.class)).collect(Collectors.toList());
        // as a fix:
        Resource mapping = addMapping(expr, props, Collections.singletonList(target));
        return asProperties(mapping);
    }

    /**
     * Adds a mapping to the graph
     *
     * @param expression resource
     * @param sources    List of source properties
     * @param targets    List of target properties
     * @return a mapping resource inside graph
     */
    protected Resource addMapping(Resource expression, List<Property> sources, List<Property> targets) {
        Resource mapping = createMapping(expression, sources, targets);
        getSource().addProperty(SPINMAP.rule, mapping);
        return mapping;
    }

    public Resource createMapping(Resource expression, List<Property> sources, List<Property> targets) {
        // todo: if no library Mapping template available - create it
        Resource res = getModel().createResource().addProperty(RDF.type, SPINMAP.mapping(sources.size(), targets.size()));
        res.addProperty(SPINMAP.context, this);
        res.addProperty(SPINMAP.expression, expression);
        processProperties(res, expression, sources, SPINMAP::sourcePredicate);
        processProperties(res, expression, targets, SPINMAP::targetPredicate);
        return res;
    }

    protected void processProperties(Resource mapping, Resource expression, List<Property> properties, IntFunction<Property> predicate) {
        for (int i = 0; i < properties.size(); i++) {
            Property src = properties.get(i);
            // todo: need to create these resources if they absent in library graph
            Property mapPredicate = predicate.apply(i + 1);
            Resource var = SPIN._arg(i + 1);
            // replace with variable
            List<Statement> res = Iter.asStream(expression.listProperties())
                    .filter(s -> Objects.equals(s.getObject(), src))
                    .collect(Collectors.toList());
            res.forEach(s -> {
                getModel().remove(s);
                getModel().add(s.getSubject(), s.getPredicate(), var);
            });
            mapping.addProperty(mapPredicate, src);
        }
    }

    @Override
    public Stream<PropertyBridge> properties() {
        // todo:
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public Context removeProperties(PropertyBridge properties) {
        // todo:
        throw new UnsupportedOperationException("TODO");
    }

    private PropertyBridge asProperties(Resource resource) {
        return new MapPropertiesImpl(resource.asNode(), getModel());
    }

    /**
     * todo: not fully ready.
     * Example of expression:
     * <pre>{@code
     * [ a  spinmapl:buildURI2 ;
     *      sp:arg1            people:secondName ;
     *      sp:arg2            people:firstName ;
     *      spinmap:source     spinmap:_source ;
     *      spinmapl:template  "beings:Being-{?1}-{?2}"
     *  ] ;
     *  }</pre>
     *
     * @param func {@link MapFunction.Call} function call to write
     * @return {@link Resource}
     */
    public Resource createExpression(MapFunction.Call func) {
        Model model = getModel();
        Resource res = model.createResource();
        Resource function = model.createResource(func.getFunction().name());
        res.addProperty(RDF.type, function);
        func.asMap().forEach((arg, value) -> {
            if (!(value instanceof String)) // todo: handle nested function call
                throw new UnsupportedOperationException("TODO");
            // todo:
            Property predicate = model.createResource(arg.name()).as(Property.class);
            res.addProperty(predicate, createArgRDFNode(arg.type(), (String) value));
        });
        return res;
    }

    /**
     * Validates a function-call against the context.
     *
     * @param func {@link MapFunction.Call} an expression.
     * @throws MapJenaException if something is wrong with function, e.g. wrong argument types.
     */
    @Override
    public void validate(MapFunction.Call func) throws MapJenaException {
        func.asMap().forEach(this::validateArg);
    }

    private void validateArg(MapFunction.Arg arg, Object value) {
        String argType = arg.type();
        if (value instanceof String) {
            createArgRDFNode(argType, (String) value);
            return;
        }
        if (MapFunctionImpl.UNDEFINED.equals(argType)) // todo: undefined means xsd:string or uri-resource
            throw new MapJenaException("TODO"); // todo: exception mechanism
        if (value instanceof MapFunction.Call) {
            String funcType = ((MapFunction.Call) value).getFunction().returnType();
            validateFuncReturnType(argType, funcType);
            validate((MapFunction.Call) value);
        }
        throw new IllegalStateException("??");
    }

    private void validateFuncReturnType(String argType, String funcType) {
        if (argType.equals(funcType)) return;
        RDFDatatype literalType = rdfTypes.getTypeByName(funcType);
        if (literalType != null)
            throw new MapJenaException("TODO");
        if (RDFS.Resource.getURI().equals(argType))
            return;
        throw new MapJenaException("TODO");
    }

    public RDFNode createArgRDFNode(String type, String value) throws MapJenaException {
        Resource uri = getModel().createResource(value);
        if (MapFunctionImpl.UNDEFINED.equals(type)) {
            if (getModel().containsResource(uri)) { // todo: what kind of property?
                type = RDF.Property.getURI();
            } else {
                type = XSD.xstring.getURI();
            }
        }
        RDFDatatype literalType = rdfTypes.getTypeByName(type);
        if (literalType != null) {
            return getModel().createTypedLiteral(value, literalType);
        }
        if (ARG_RESOURCE_MAPPING.getOrDefault(type, r -> false).test(uri)) {
            return uri;
        }
        throw new MapJenaException("TODO"); // todo: exception mechanism
    }
}