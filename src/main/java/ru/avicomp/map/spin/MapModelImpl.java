package ru.avicomp.map.spin;

import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.topbraid.spin.vocabulary.SPINMAP;
import org.topbraid.spin.vocabulary.SPINMAPL;
import ru.avicomp.map.Context;
import ru.avicomp.map.MapFunction;
import ru.avicomp.map.MapJenaException;
import ru.avicomp.map.MapModel;
import ru.avicomp.map.spin.impl.MapContextImpl;
import ru.avicomp.ontapi.jena.UnionGraph;
import ru.avicomp.ontapi.jena.impl.OntGraphModelImpl;
import ru.avicomp.ontapi.jena.impl.conf.OntPersonality;
import ru.avicomp.ontapi.jena.model.*;
import ru.avicomp.ontapi.jena.utils.Graphs;
import ru.avicomp.ontapi.jena.utils.Models;
import ru.avicomp.ontapi.jena.vocabulary.OWL;
import ru.avicomp.ontapi.jena.vocabulary.RDF;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by @szuev on 10.04.2018.
 */
@SuppressWarnings("WeakerAccess")
public class MapModelImpl extends OntGraphModelImpl implements MapModel {
    private static final String CONTEXT_TEMPLATE = "Context-%s-%s";
    private final MapManagerImpl manager;

    public MapModelImpl(UnionGraph base, OntPersonality personality, MapManagerImpl manager) {
        super(base, personality);
        this.manager = manager;
    }

    @Override
    public OntID getID() {
        return getNodeAs(Graphs.ontologyNode(getBaseGraph())
                .orElseGet(() -> createResource().addProperty(RDF.type, OWL.Ontology).asNode()), OntID.class);
    }

    @Override
    public OntID setID(String uri) {
        return getNodeAs(OntGraphModelImpl.createOntologyID(getBaseModel(), uri).asNode(), OntID.class);
    }

    @Override
    public Stream<OntGraphModel> imports(OntPersonality personality) {
        return imports(personality, false);
    }

    public Stream<OntGraphModel> imports(OntPersonality personality, boolean withLibrary) {
        return super.imports(personality).filter(model -> withLibrary || !SystemModels.graphs().keySet().contains(model.getID().getURI()));
    }

    @Override
    public Stream<Context> contexts() {
        return listContexts().map(Context.class::cast);
    }

    public Stream<MapContextImpl> listContexts() {
        return statements(null, RDF.type, SPINMAP.Context)
                .map(OntStatement::getSubject)
                .filter(s -> s.objects(SPINMAP.targetClass, OntClass.class).findAny().isPresent())
                .filter(s -> s.objects(SPINMAP.sourceClass, OntClass.class).findAny().isPresent())
                .map(this::asContext);
    }

    @Override
    public MapContextImpl createContext(OntCE source, OntCE target) {
        return contexts()
                .filter(s -> Objects.equals(s.getSource(), source))
                .filter(s -> Objects.equals(s.getTarget(), target))
                .map(MapContextImpl.class::cast)
                .findFirst()
                .orElseGet(() -> asContext(makeContext(source, target)));
    }

    public MapContextImpl asContext(Resource context) {
        return new MapContextImpl(context.asNode(), this);
    }

    @Override
    public MapModelImpl removeContext(Context context) {
        MapContextImpl c = ((MapContextImpl) MapJenaException.notNull(context, "Null context"));
        if (getManager().generateNamedIndividuals()) {
            statements(null, RDF.type, SPINMAP.Context)
                    .map(OntStatement::getSubject)
                    .filter(s -> s.hasProperty(SPINMAP.targetClass, OWL.NamedIndividual))
                    .filter(s -> s.hasProperty(SPINMAP.sourceClass, c.getTarget()))
                    .map(this::asContext)
                    .findFirst().ifPresent(this::deleteContext);
        }
        deleteContext(c);
        // todo: remove also custom functions
        return this;
    }

    public void deleteContext(MapContextImpl context) {
        // delete rules:
        Set<Statement> rules = context.listRules().collect(Collectors.toSet());
        rules.forEach(s -> {
            Models.deleteAll(s.getObject().asResource());
            remove(s);
        });
        // delete declaration:
        Models.deleteAll(context);
    }

    /**
     * Creates a {@code spinmap:Context} resource for specified {@link OntCE OWL Class Expression}s.
     * <pre>{@code
     * _:x rdf:type spinmap:Context ;
     *   spinmap:sourceClass <src> ;
     *   spinmap:targetClass <dst> ;
     * }</pre>
     *
     * @param source {@link OntCE}
     * @param target {@link OntCE}
     * @return {@link Resource}
     */
    public Resource makeContext(OntCE source, OntCE target) {
        // ensue all related models are imported:
        Stream.of(MapJenaException.notNull(source, "Null source CE"),
                MapJenaException.notNull(target, "Null target CE"))
                .map(OntObject::getModel)
                .forEach(MapModelImpl.this::addImport);
        Resource res = makeContext(source.asResource(), target.asResource());
        if (getManager().generateNamedIndividuals()) {
            MapFunction.Call expr = getManager().getFunction(SPINMAPL.self.getURI()).createFunctionCall().build();
            asContext(makeContext(target.asResource(), OWL.NamedIndividual)).addExpression(expr);
        }
        return res;
    }

    protected Resource makeContext(Resource source, Resource target) {
        String iri = getID().getURI();
        Resource res = null;
        if (iri != null && !iri.contains("#")) {
            res = createResource(iri + "#" + String.format(CONTEXT_TEMPLATE, getLocalName(source), getLocalName(target)));
            if (containsResource(res)) { // found different resource with the same local name
                res = null;
            }
        }
        if (res == null) { // anonymous context
            res = createResource();
        }
        res.addProperty(RDF.type, SPINMAP.Context);
        res.addProperty(SPINMAP.sourceClass, source);
        res.addProperty(SPINMAP.targetClass, target);
        return res;
    }

    public static String getLocalName(Resource resource) {
        return resource.isURIResource() ? resource.getLocalName() : resource.getId().getLabelString();
    }

    public MapManagerImpl getManager() {
        return manager;
    }

}
