package ru.avicomp.map.spin;

import org.apache.jena.atlas.iterator.Iter;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.XSD;
import org.topbraid.spin.vocabulary.SPIN;
import org.topbraid.spin.vocabulary.SPINMAP;
import org.topbraid.spin.vocabulary.SPL;
import ru.avicomp.map.MapFunction;
import ru.avicomp.map.MapJenaException;
import ru.avicomp.map.spin.vocabulary.AVC;
import ru.avicomp.ontapi.jena.utils.Models;
import ru.avicomp.ontapi.jena.vocabulary.OWL;
import ru.avicomp.ontapi.jena.vocabulary.RDF;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static ru.avicomp.map.spin.Exceptions.*;

/**
 * A spin based implementation of {@link MapFunction}.
 * Created by @szuev on 09.04.2018.
 */
@SuppressWarnings("WeakerAccess")
public class MapFunctionImpl implements MapFunction {
    public static final String STRING_VALUE_SEPARATOR = "\n";
    private final org.topbraid.spin.model.Module func;
    private List<Arg> arguments;

    public MapFunctionImpl(org.topbraid.spin.model.Function func) {
        this.func = Objects.requireNonNull(func, "Null " + org.topbraid.spin.model.Function.class.getName());
    }

    @Override
    public String name() {
        return func.getURI();
    }

    @Override
    public String returnType() {
        Resource r = func instanceof org.topbraid.spin.model.Function ? ((org.topbraid.spin.model.Function) func).getReturnType() : null;
        return (r == null ? AVC.undefined : r).getURI();
    }

    public List<Arg> getArguments() {
        return arguments == null ? arguments = func.getArguments(true).stream().map(ArgImpl::new).collect(Collectors.toList()) : arguments;
    }

    @Override
    public Stream<Arg> args() {
        return getArguments().stream();
    }

    public Optional<Arg> arg(String predicate) {
        return args().filter(a -> Objects.equals(a.name(), predicate)).findFirst();
    }

    @Override
    public Arg getArg(String predicate) throws MapJenaException {
        return arg(predicate)
                .orElseThrow(() -> exception(FUNCTION_NONEXISTENT_ARGUMENT).add(Key.ARG, predicate).build());
    }

    @Override
    public boolean isTarget() {
        return func.hasProperty(RDF.type, SPINMAP.TargetFunction);
    }

    @Override
    public boolean isBoolean() {
        return XSD.xboolean.getURI().equals(returnType());
    }

    /**
     * Answers iff this function is custom, i.e. does not belong to the original spin family.
     * Custom functions must be directly added to the final mapping graph for compatibility with Topbraid Composer.
     *
     * @return boolean
     */
    public boolean isCustom() {
        return Objects.equals(AVC.NS, func.getNameSpace());
    }

    public boolean isPrivate() {
        return func instanceof org.topbraid.spin.model.Function && ((org.topbraid.spin.model.Function) func).isPrivate();
    }

    public boolean isAbstract() {
        return func.isAbstract();
    }

    public boolean isDeprecated() {
        return func.hasProperty(RDF.type, OWL.DeprecatedClass);
    }

    public boolean isHidden() {
        return func.hasProperty(AVC.hidden);
    }

    /**
     * Returns resource attached to the library model
     *
     * @return {@link Resource}
     */
    public Resource asResource() {
        return func;
    }

    @Override
    public Builder create() {
        return new BuilderImpl();
    }

    @Override
    public String getComment(String lang) {
        return Models.langValues(func, RDFS.comment, lang).collect(Collectors.joining(STRING_VALUE_SEPARATOR));
    }

    @Override
    public String getLabel(String lang) {
        return Models.langValues(func, RDFS.label, lang).collect(Collectors.joining(STRING_VALUE_SEPARATOR));
    }

    public Model getModel() {
        return func.getModel();
    }

    public String toString(PrefixMapping pm) {
        return String.format("%s [%s](%s)",
                pm.shortForm(returnType()),
                pm.shortForm(name()), args()
                        .map(ArgImpl.class::cast)
                        .map(a -> a.toString(pm))
                        .collect(Collectors.joining(", ")));
    }

    private Exceptions.Builder exception(Exceptions code) {
        return code.create().add(Key.FUNCTION, name());
    }

    public class ArgImpl implements Arg {
        private final org.topbraid.spin.model.Argument arg;

        public ArgImpl(org.topbraid.spin.model.Argument arg) {
            this.arg = arg;
        }

        @Override
        public String name() {
            return arg.getPredicate().getURI();
        }

        @Override
        public String type() {
            return getValueType().getURI();
        }

        public Resource getValueType() {
            Optional<Resource> r = refinedConstraints()
                    .filter(s -> Objects.equals(s.getPredicate(), SPL.valueType))
                    .map(Statement::getObject)
                    .filter(RDFNode::isURIResource)
                    .map(RDFNode::asResource)
                    .findFirst();
            if (r.isPresent()) return r.get();
            Resource res = arg.getValueType();
            return res == null ? AVC.undefined : res;
        }

        public Stream<Statement> refinedConstraints() {
            return Iter.asStream(func.listProperties(AVC.constraint))
                    .map(Statement::getObject)
                    .filter(RDFNode::isAnon)
                    .map(RDFNode::asResource)
                    .filter(r -> r.hasProperty(SPL.predicate, arg.getPredicate()))
                    .map(Resource::listProperties)
                    .flatMap(Iter::asStream);
        }

        @Override
        public String defaultValue() {
            RDFNode r = arg.getDefaultValue();
            return r == null ? null : r.toString();
        }

        @Override
        public boolean isOptional() {
            return arg.isOptional();
        }

        @Override
        public boolean isAssignable() {
            return !isInherit();
        }

        @Override
        public MapFunction getFunction() {
            return MapFunctionImpl.this;
        }

        /**
         * Checks if it is a direct argument or it goes from superclass.
         * Direct arguments can be used to make function call, inherited should be ignored.
         *
         * @return false if it is normal argument, i.e. it is ready to use.
         */
        public boolean isInherit() {
            return !func.hasProperty(SPIN.constraint, arg);
        }

        @Override
        public String getComment(String lang) {
            return Models.langValues(arg, RDFS.comment, lang).collect(Collectors.joining(STRING_VALUE_SEPARATOR));
        }

        @Override
        public String getLabel(String lang) {
            return Models.langValues(arg, RDFS.label, lang).collect(Collectors.joining(STRING_VALUE_SEPARATOR));
        }

        public String toString(PrefixMapping pm) {
            return String.format("%s%s=%s", pm.shortForm(name()), info(), pm.shortForm(type()));
        }

        private String info() {
            List<String> res = new ArrayList<>(2);
            if (isOptional()) res.add("*");
            if (isInherit()) res.add("i");
            return res.isEmpty() ? "" : res.stream().collect(Collectors.joining(",", "(", ")"));
        }
    }

    public class BuilderImpl implements Builder {
        // either string or builder
        private final Map<String, Object> input = new HashMap<>();

        @Override
        public Builder add(String arg, String value) {
            return put(arg, value);
        }

        @Override
        public Builder add(String arg, Builder other) {
            return put(arg, other);
        }

        private Builder put(String predicate, Object val) {
            MapJenaException.notNull(val, "Null argument value");
            Arg arg = getFunction().getArg(predicate);
            if (!arg.isAssignable())
                throw exception(FUNCTION_WRONG_ARGUMENT).add(Key.ARG, predicate).build();

            if (!(val instanceof String)) {
                if (val instanceof Builder) {
                    if (this.equals(val)) {
                        throw exception(FUNCTION_SELF_CALL).add(Key.ARG, predicate).build();
                    }
                    // todo: if arg is rdf:Property no nested function must be allowed
                    /*if (AVC.undefined.getURI().equals(((Builder) val).getFunction().returnType())) {
                        // todo: undefined should be allowed
                        throw new MapJenaException("Void: " + ((Builder) val).getFunction());
                    }*/
                } else {
                    throw new IllegalArgumentException("Wrong argument type: " + val.getClass().getName() + ", " + val);
                }
            }
            input.put(arg.name(), val);
            return this;
        }

        @Override
        public MapFunction getFunction() {
            return MapFunctionImpl.this;
        }

        @Override
        public Call build() throws MapJenaException {
            Map<String, Object> map = input.entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey,
                            e -> e.getValue() instanceof Builder ? ((Builder) e.getValue()).build() : e.getValue()));
            if (MapFunctionImpl.this.isTarget()) {
                // Most of spin-map target function calls should have spin:_source variable assigned on this argument,
                // although it does not seem it is really needed.
                MapFunctionImpl.this.arg(SPINMAP.source.getURI())
                        .ifPresent(a -> map.put(a.name(), SPINMAP.sourceVariable.getURI()));
            }
            // check all required arguments are assigned
            Exceptions.Builder error = exception(FUNCTION_NO_REQUIRED_ARG);
            getFunction().args().forEach(a -> {
                if (map.containsKey(a.name()) || a.isOptional())
                    return;
                String def = a.defaultValue();
                if (def == null) {
                    error.add(Key.ARG, a.name());
                } else {
                    map.put(a.name(), def);
                }
            });
            if (error.has(Key.ARG))
                throw error.build();
            return new CallImpl(map);
        }
    }

    public class CallImpl implements Call {
        // either string or another function calls
        private final Map<String, Object> parameters;

        private CallImpl(Map<String, Object> args) {
            this.parameters = args;
        }

        @Override
        public Map<Arg, Object> asMap() {
            return parameters.entrySet().stream()
                    .collect(Collectors.toMap(e -> getFunction().getArg(e.getKey()), Map.Entry::getValue));
        }


        @Override
        public MapFunctionImpl getFunction() {
            return MapFunctionImpl.this;
        }

        @Override
        public Builder asUnmodifiableBuilder() {
            return new Builder() {
                @Override
                public Builder add(String arg, String value) {
                    throw new MapJenaException.Unsupported();
                }

                @Override
                public Builder add(String arg, Builder other) {
                    throw new MapJenaException.Unsupported();
                }

                @Override
                public MapFunction getFunction() {
                    return MapFunctionImpl.this;
                }

                @Override
                public Call build() throws MapJenaException {
                    return CallImpl.this;
                }
            };
        }

    }
}
