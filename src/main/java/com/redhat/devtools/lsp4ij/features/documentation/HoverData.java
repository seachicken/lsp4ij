/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.documentation;

import com.redhat.devtools.lsp4ij.LanguageServerItem;
import org.eclipse.lsp4j.Hover;
import org.jetbrains.annotations.NotNull;

/**
 * Hover Data
 *
 * @param hover               the LSP Hover
 * @param languageServer         the language server which has created the codeLens.
 */
record HoverData(@NotNull Hover hover,
                 @NotNull LanguageServerItem languageServer) {

}