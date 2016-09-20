package org.codice.ddf.catalog.ui.metacard.associations;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.codice.ddf.catalog.ui.util.EndpointUtil;
import org.opengis.filter.Filter;
import org.opengis.filter.sort.SortBy;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import ddf.catalog.CatalogFramework;
import ddf.catalog.data.Attribute;
import ddf.catalog.data.Metacard;
import ddf.catalog.data.Result;
import ddf.catalog.data.impl.AttributeImpl;
import ddf.catalog.federation.FederationException;
import ddf.catalog.operation.QueryResponse;
import ddf.catalog.operation.impl.QueryImpl;
import ddf.catalog.operation.impl.QueryRequestImpl;
import ddf.catalog.operation.impl.UpdateRequestImpl;
import ddf.catalog.source.IngestException;
import ddf.catalog.source.SourceUnavailableException;
import ddf.catalog.source.UnsupportedQueryException;

public class Associated {

    private static final String ASSOCIATION_PREFIX = "metacard.associations.";

    private static final Set<String> ASSOCIATION_TYPES = ImmutableSet.of(Metacard.DERIVED,
            Metacard.RELATED);

    private final EndpointUtil util;

    private final CatalogFramework catalogFramework;

    public Associated(EndpointUtil util, CatalogFramework catalogFramework) {
        this.util = util;
        this.catalogFramework = catalogFramework;
    }

    public Collection<Edge> getAssociations(String metacardId)
            throws UnsupportedQueryException, SourceUnavailableException, FederationException {
        Map<String, Metacard> metacardMap = query(forRootAndParents(metacardId));

        Metacard root = metacardMap.get(metacardId);
        Collection<Metacard> parents = metacardMap.values()
                .stream()
                .filter(m -> !m.getId()
                        .equals(metacardId))
                .collect(Collectors.toList());

        Map<String, Metacard> childMetacardMap = query(forChildAssociations(root));

        Collection<Edge> parentEdges = createParentEdges(parents, root);
        Collection<Edge> childrenEdges = createChildEdges(childMetacardMap.values(), root);

        Collection<Edge> edges = Stream.of(parentEdges, childrenEdges)
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
        return edges;
    }

    public void putAssociations(String id, Collection<Edge> edges)
            throws UnsupportedQueryException, SourceUnavailableException, FederationException,
            IngestException {
        Collection<Edge> oldEdges = getAssociations(id);

        List<String> ids = Stream.concat(oldEdges.stream(), edges.stream())
                .flatMap(e -> Stream.of(e.child, e.parent))
                .filter(Objects::nonNull)
                .map(m -> m.get(Metacard.ID))
                .filter(Objects::nonNull)
                .map(Object::toString)
                .distinct()
                .collect(Collectors.toList());

        Map<String, Metacard> metacards = util.getMetacards(ids, Metacard.DEFAULT_TAG)
                .entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        e -> e.getValue()
                                .getMetacard()));

        Map<String, Metacard> changedMetacards = new HashMap<>();
        Set<Edge> oldEdgeSet = new HashSet<>(oldEdges);
        Set<Edge> newEdgeSet = new HashSet<>(edges);

        Set<Edge> oldDiff = Sets.difference(oldEdgeSet, newEdgeSet);
        Set<Edge> newDiff = Sets.difference(newEdgeSet, oldEdgeSet);

        for (Edge edge : oldDiff) {
            removeEdge(edge, metacards, changedMetacards);
        }
        for (Edge edge : newDiff) {
            addEdge(edge, metacards, changedMetacards);
        }

        if (changedMetacards.isEmpty()) {
            return;
        }

        catalogFramework.update(new UpdateRequestImpl(changedMetacards.keySet()
                .toArray(new String[0]), new ArrayList<>(changedMetacards.values())));

    }

    private void removeEdge(Edge edge, Map<String, Metacard> metacards,
            /*Mutable*/ Map<String, Metacard> changedMetacards) {
        String id = edge.parent.get(Metacard.ID)
                .toString();
        Metacard target = changedMetacards.getOrDefault(id, metacards.get(id));
        ArrayList<String> values = Optional.of(target)
                .map(m -> m.getAttribute(edge.relation))
                .map(Attribute::getValues)
                .map(util::getStringList)
                .orElseGet(ArrayList::new);
        values.remove(edge.child.get(Metacard.ID)
                .toString());
        target.setAttribute(new AttributeImpl(edge.relation, values));
        changedMetacards.put(id, target);
    }

    private void addEdge(Edge edge, Map<String, Metacard> metacards,
            Map<String, Metacard> changedMetacards) {
        String id = edge.parent.get(Metacard.ID)
                .toString();
        Metacard target = changedMetacards.getOrDefault(id, metacards.get(id));
        ArrayList<String> values = Optional.of(target)
                .map(m -> m.getAttribute(edge.relation))
                .map(Attribute::getValues)
                .map(util::getStringList)
                .orElseGet(ArrayList::new);
        values.add(edge.child.get(Metacard.ID)
                .toString());
        target.setAttribute(new AttributeImpl(edge.relation, values));
        changedMetacards.put(id, target);
    }

    private Collection<Edge> createChildEdges(Collection<Metacard> children, Metacard root) {
        List<Edge> edges = new ArrayList<>();
        for (Metacard child : children) {
            List<String> relations = getRelationsToChild(root, child);
            edges.addAll(relations.stream()
                    .map(relation -> new Edge(root, child, relation))
                    .collect(Collectors.toList()));
        }
        return edges;
    }

    private Collection<Edge> createParentEdges(Collection<Metacard> parents, Metacard root) {
        List<Edge> edges = new ArrayList<>();
        for (Metacard parent : parents) {
            List<String> relations = getRelationsToChild(parent, root);
            edges.addAll(relations.stream()
                    .map(relation -> new Edge(parent, root, relation))
                    .collect(Collectors.toList()));
        }
        return edges;
    }

    private Filter forRootAndParents(String rootId) {
        Filter root = util.getFilterBuilder()
                .attribute(Metacard.ID)
                .is()
                .equalTo()
                .text(rootId);
        Filter related = util.getFilterBuilder()
                .attribute(Metacard.RELATED)
                .is()
                .like()
                .text(rootId);
        Filter derived = util.getFilterBuilder()
                .attribute(Metacard.DERIVED)
                .is()
                .like()
                .text(rootId);
        Filter parents = util.getFilterBuilder()
                .anyOf(related, derived);

        return util.getFilterBuilder()
                .anyOf(root, parents);
    }

    private Filter forChildAssociations(Metacard metacard) {
        Set<String> childIds = ASSOCIATION_TYPES.stream()
                .map(metacard::getAttribute)
                .filter(Objects::nonNull)
                .map(Attribute::getValues)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .collect(Collectors.toSet());

        return util.getFilterBuilder()
                .anyOf(childIds.stream()
                        .map(id -> util.getFilterBuilder()
                                .attribute(Metacard.ID)
                                .is()
                                .equalTo()
                                .text(id))
                        .collect(Collectors.toList()));

    }

    private Map<String, Metacard> query(Filter filter)
            throws UnsupportedQueryException, SourceUnavailableException, FederationException {
        QueryResponse query = catalogFramework.query(new QueryRequestImpl(new QueryImpl(filter,
                1,
                0,
                SortBy.NATURAL_ORDER,
                false,
                TimeUnit.SECONDS.toMillis(30)), true));

        return query.getResults()
                .stream()
                .map(Result::getMetacard)
                .collect(Collectors.toMap(Metacard::getId, Function.identity()));
    }

    private List<String> getRelationsToChild(Metacard parent, Metacard child) {
        List<String> relations = new ArrayList<>();
        for (String associationType : ASSOCIATION_TYPES) {
            if (Optional.of(parent)
                    .map(m -> m.getAttribute(associationType))
                    .map(Attribute::getValues)
                    .map(util::getStringList)
                    .map(l -> l.contains(child.getId()))
                    .orElse(false)) {
                relations.add(associationType);
            }
        }

        return relations;
    }

    public class Edge {
        private Map<String, Object> parent;

        private Map<String, Object> child;

        private String relation;

        public Edge(Metacard parent, Metacard child, String relation) {
            this.parent = util.getMetacardMap(parent);
            this.child = util.getMetacardMap(child);
            this.relation = relation;
        }

        public int hashCode() {
            return new HashCodeBuilder().append(parent.get(Metacard.ID)
                    .toString())
                    .append(child.get(Metacard.ID)
                            .toString())
                    .append(relation)
                    .build();
        }

        public boolean equals(Object o) {
            if (o == null) {
                return false;
            }
            if (o == this) {
                return true;
            }

            Edge rhs = (Edge) o;
            return new EqualsBuilder().append(parent.get(Metacard.ID)
                            .toString(),
                    rhs.parent.get(Metacard.ID)
                            .toString())
                    .append(child.get(Metacard.ID)
                                    .toString(),
                            rhs.child.get(Metacard.ID)
                                    .toString())
                    .append(relation, rhs.relation)
                    .build();
        }

        @Override
        public String toString() {
            return String.format("%s [%s]-> %s",
                    parent.get("id")
                            .toString(),
                    relation,
                    child.get("id")
                            .toString());
        }
    }

}
