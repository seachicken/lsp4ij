/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * CppCXY
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.documentSymbol;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.LSPRequestConstants;
import com.redhat.devtools.lsp4ij.LanguageServerItem;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.lsp4ij.features.AbstractLSPDocumentFeatureSupport;
import com.redhat.devtools.lsp4ij.internal.CancellationSupport;
import com.redhat.devtools.lsp4ij.internal.CompletableFutures;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.SymbolInformation;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * LSP document symbol support which loads and caches symbol result by consuming:
 *
 * <ul>
 *     <li>LSP 'textDocument/documentSymbol' requests</li>
 * </ul>
 */
public class LSPDocumentSymbolSupport extends AbstractLSPDocumentFeatureSupport<DocumentSymbolParams, List<DocumentSymbolData>> {
    public LSPDocumentSymbolSupport(@NotNull PsiFile file) {
        super(file);
    }

    public CompletableFuture<List<DocumentSymbolData>> getDocumentSymbols(DocumentSymbolParams params) {
        return super.getFeatureData(params);
    }

    @Override
    protected CompletableFuture<List<DocumentSymbolData>> doLoad(DocumentSymbolParams documentSymbolParams, CancellationSupport cancellationSupport) {
        PsiFile file = super.getFile();
        return getDocumentSymbols(file, documentSymbolParams, cancellationSupport);
    }

    private static @NotNull CompletableFuture<List<DocumentSymbolData>> getDocumentSymbols(@NotNull PsiFile psiFile,
                                                                                           @NotNull DocumentSymbolParams params,
                                                                                           @NotNull CancellationSupport cancellationSupport) {

        Project project = psiFile.getProject();
        VirtualFile file = psiFile.getVirtualFile();
        return LanguageServiceAccessor.getInstance(project)
                .getLanguageServers(file, LanguageServerItem::isDocumentSymbolSupported)
                .thenComposeAsync(languageServers -> {
                    // Here languageServers is the list of language servers which matches the given file
                    // and which have document link capability
                    if (languageServers.isEmpty()) {
                        return CompletableFuture.completedFuture(Collections.emptyList());
                    }

                    // Collect list of textDocument/documentSymbol future for each language servers
                    List<CompletableFuture<List<DocumentSymbolData>>> documentSymbolInformationPerServerFutures = languageServers
                            .stream()
                            .map(languageServer -> getDocumentSymbolsFor(params, languageServer, psiFile, cancellationSupport))
                            .toList();

                    // Merge list of textDocument/documentSymbol future in one future which return the list of document link
                    return CompletableFutures.mergeInOneFuture(documentSymbolInformationPerServerFutures, cancellationSupport);
                });
    }

    private static CompletableFuture<List<DocumentSymbolData>> getDocumentSymbolsFor(@NotNull DocumentSymbolParams params,
                                                                                     @NotNull LanguageServerItem languageServer,
                                                                                     @NotNull PsiFile psiFile,
                                                                                     @NotNull CancellationSupport cancellationSupport) {
        return cancellationSupport.execute(languageServer
                        .getTextDocumentService()
                        .documentSymbol(params), languageServer, LSPRequestConstants.TEXT_DOCUMENT_DOCUMENT_SYMBOL)
                .thenApplyAsync(documentSymbol -> {
                    if (documentSymbol == null) {
                        // textDocument/documentSymbol may return null
                        return Collections.emptyList();
                    }
                    return documentSymbol.stream()
                            .filter(Objects::nonNull)
                            .map(symbol -> {
                                if (symbol.isLeft()){
                                    var si = symbol.getLeft();
                                    return new DocumentSymbolData(convertToDocumentSymbol(si), psiFile);
                                }
                                else {
                                    return new DocumentSymbolData(symbol.getRight(), psiFile);
                                }
                            })
                            .toList();
                });
    }

    private static DocumentSymbol convertToDocumentSymbol(SymbolInformation symbolInformation) {
        var name = symbolInformation.getName();
        var kind = symbolInformation.getKind();
        var range = symbolInformation.getLocation().getRange();
        return new DocumentSymbol(name, kind, range, range);
    }
}