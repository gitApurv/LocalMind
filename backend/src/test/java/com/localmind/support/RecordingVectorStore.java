package com.localmind.support;

import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;

/**
 * Test support: an in-memory {@link VectorStore} that records added documents, searches and filter deletes, returns
 * canned search results and can be told to fail, so tests need no Qdrant.
 */
public final class RecordingVectorStore implements VectorStore {

    private final List<Document> added = new ArrayList<>();
    private final List<SearchRequest> searches = new ArrayList<>();
    private final List<Filter.Expression> deleteFilters = new ArrayList<>();
    private List<Document> searchResults = List.of();
    private RuntimeException addFailure;
    private boolean keepFirstDocumentOnFailure;
    private RuntimeException searchFailure;

    public void returnOnSearch(List<Document> results) {
        searchResults = results;
    }

    public void failOnAdd(RuntimeException failure) {
        addFailure = failure;
        keepFirstDocumentOnFailure = false;
    }

    public void failOnAddAfterFirstDocument(RuntimeException failure) {
        addFailure = failure;
        keepFirstDocumentOnFailure = true;
    }

    public void failOnSearch(RuntimeException failure) {
        searchFailure = failure;
    }

    public List<Document> added() {
        return added;
    }

    public List<SearchRequest> searches() {
        return searches;
    }

    public SearchRequest lastSearch() {
        return searches.isEmpty() ? null : searches.getLast();
    }

    public List<Filter.Expression> deleteFilters() {
        return deleteFilters;
    }

    @Override
    public void add(List<Document> documents) {
        if (addFailure == null) {
            added.addAll(documents);
            return;
        }
        if (keepFirstDocumentOnFailure) {
            added.add(documents.getFirst());
        }
        throw addFailure;
    }

    @Override
    public void delete(List<String> idList) {
    }

    @Override
    public void delete(Filter.Expression filterExpression) {
        deleteFilters.add(filterExpression);
    }

    @Override
    public List<Document> similaritySearch(SearchRequest request) {
        searches.add(request);
        if (searchFailure != null) {
            throw searchFailure;
        }
        return searchResults;
    }
}
