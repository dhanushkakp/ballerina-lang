/*
 * Copyright (c) 2018, WSO2 Inc. (http://wso2.com) All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ballerinalang.langserver.extensions.ballerina.document;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.ballerina.compiler.api.SemanticModel;
import io.ballerina.compiler.syntax.tree.SyntaxTree;
import org.ballerinalang.diagramutil.DiagramUtil;
import org.ballerinalang.langserver.BallerinaLanguageServer;
import org.ballerinalang.langserver.common.utils.CommonUtil;
import org.ballerinalang.langserver.commons.LSContext;
import org.ballerinalang.langserver.commons.workspace.WorkspaceManager;
import org.ballerinalang.langserver.compiler.DocumentServiceKeys;
import org.ballerinalang.langserver.compiler.common.modal.SymbolMetaInfo;
import org.ballerinalang.langserver.compiler.format.JSONGenerationException;
import org.ballerinalang.langserver.compiler.format.TextDocumentFormatUtil;
import org.ballerinalang.langserver.compiler.sourcegen.FormattingSourceGen;
import org.ballerinalang.langserver.extensions.VisibleEndpointVisitor;
import org.eclipse.lsp4j.Position;
import org.wso2.ballerinalang.compiler.tree.BLangCompilationUnit;
import org.wso2.ballerinalang.compiler.tree.BLangNode;
import org.wso2.ballerinalang.compiler.tree.BLangPackage;
import org.wso2.ballerinalang.compiler.util.CompilerContext;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.ballerinalang.langserver.compiler.LSClientLogger.logError;
import static org.ballerinalang.langserver.compiler.LSCompilerUtil.getProjectDir;

/**
 * Implementation of Ballerina Document extension for Language Server.
 *
 * @since 0.981.2
 */
public class BallerinaDocumentServiceImpl implements BallerinaDocumentService {

    private final BallerinaLanguageServer ballerinaLanguageServer;
    private final WorkspaceManager workspaceManager;
    public static final LSContext.Key<String> UPDATED_SOURCE = new LSContext.Key<>();

    public BallerinaDocumentServiceImpl(BallerinaLanguageServer ballerinaLanguageServer,
                                        WorkspaceManager workspaceManager) {
        this.ballerinaLanguageServer = ballerinaLanguageServer;
        this.workspaceManager = workspaceManager;
    }

    @Override
    public CompletableFuture<BallerinaASTResponse> ast(BallerinaASTRequest request) {
        BallerinaASTResponse reply = new BallerinaASTResponse();
//        String fileUri = request.getDocumentIdentifier().getUri();
//        Optional<Path> filePath = CommonUtil.getPathFromURI(fileUri);
//        if (!filePath.isPresent()) {
//            return CompletableFuture.supplyAsync(() -> reply);
//        }
//        Path compilationPath = getUntitledFilePath(filePath.get().toString()).orElse(filePath.get());
//        Optional<Lock> lock = documentManager.lockFile(compilationPath);
//        try {
//            LSContext astContext = new DocumentOperationContext
//                    .DocumentOperationContextBuilder(LSContextOperation.DOC_SERVICE_AST)
//                    .withCommonParams(null, fileUri, documentManager)
//                    .build();
//            LSModuleCompiler.getBLangPackage(astContext, this.documentManager, false, false);
//            reply.setAst(getTreeForContent(astContext));
//            reply.setParseSuccess(isParseSuccess(astContext));
//        } catch (Throwable e) {
//            reply.setParseSuccess(false);
//            String msg = "Operation 'ballerinaDocument/ast' failed!";
//            logError(msg, e, request.getDocumentIdentifier(), (Position) null);
//        } finally {
//            lock.ifPresent(Lock::unlock);
//        }
        return CompletableFuture.supplyAsync(() -> reply);
    }

    private boolean isParseSuccess(LSContext astContext) {
        // TODO: Revisit this. Can never be false.
        return true;
    }

    @Override
    public CompletableFuture<BallerinaSyntaxTreeResponse> syntaxTree(BallerinaSyntaxTreeRequest request) {
        BallerinaSyntaxTreeResponse reply = new BallerinaSyntaxTreeResponse();
        String fileUri = request.getDocumentIdentifier().getUri();
        Optional<Path> filePath = CommonUtil.getPathFromURI(fileUri);
        if (filePath.isEmpty()) {
            return CompletableFuture.supplyAsync(() -> reply);
        }

        try {
            // Get the syntax tree for the document.
            Optional<SyntaxTree> syntaxTree = this.workspaceManager.syntaxTree(filePath.get());
            if (syntaxTree.isEmpty()) {
                return CompletableFuture.supplyAsync(() -> reply);
            }

            // Get the semantic model.
            Optional<SemanticModel> semanticModel = this.workspaceManager.semanticModel(filePath.get());

            // Get the generated syntax tree JSON with type info.
            JsonElement jsonSyntaxTree = DiagramUtil
                    .getSyntaxTreeJSON(filePath.get().getFileName().toString(), syntaxTree.get(), semanticModel.get());

            // Preparing the response.
            reply.setSource(syntaxTree.get().toSourceCode());
            reply.setSyntaxTree(jsonSyntaxTree);
            reply.setParseSuccess(reply.getSyntaxTree() != null);
        } catch (Throwable e) {
            reply.setParseSuccess(false);
            String msg = "Operation 'ballerinaDocument/syntaxTree' failed!";
            logError(msg, e, request.getDocumentIdentifier(), (Position) null);
        }
        return CompletableFuture.supplyAsync(() -> reply);
    }

    @Override
    public CompletableFuture<BallerinaSyntaxTreeResponse> syntaxTreeModify(BallerinaSyntaxTreeModifyRequest request) {
        BallerinaSyntaxTreeResponse reply = new BallerinaSyntaxTreeResponse();
        String fileUri = request.getDocumentIdentifier().getUri();
        Optional<Path> filePath = CommonUtil.getPathFromURI(fileUri);
        if (!filePath.isPresent()) {
            return CompletableFuture.supplyAsync(() -> reply);
        }

        try {
//
//            // Apply modifications.
//            LSContext astContext = BallerinaTreeModifyUtil.modifyTree(request.getAstModifications(), fileUri,
//                    compilationPath, documentManager);
//            BLangPackage bLangPackage = LSModuleCompiler.getBLangPackage(astContext, this.documentManager, true,
//                    false);
//
//            // Get the Visible endpoints.
//            CompilerContext compilerContext = astContext.get(DocumentServiceKeys.COMPILER_CONTEXT_KEY);
//            VisibleEndpointVisitor visibleEndpointVisitor = new VisibleEndpointVisitor(compilerContext);
//            bLangPackage.accept(visibleEndpointVisitor);
//            Map<BLangNode, List<SymbolMetaInfo>> visibleEPsByNode = visibleEndpointVisitor.getVisibleEPsByNode();
//
//            // Get the syntax tree for the document.
//            TextDocument textDocument = documentManager.getTree(compilationPath).textDocument();
//            SyntaxTree syntaxTree = SyntaxTree.from(textDocument, compilationPath.toString());
//
//            // Get the generated syntax tree JSON with type info.
//            JsonElement jsonSyntaxTree = TextDocumentFormatUtil.getSyntaxTreeJSON(syntaxTree, bLangPackage,
//                    visibleEPsByNode);
//
//            // Preparing the response.
//            reply.setSource(syntaxTree.toSourceCode());
//            reply.setSyntaxTree(jsonSyntaxTree);
//            reply.setParseSuccess(reply.getSyntaxTree() != null);
        } catch (Throwable e) {
//            reply.setParseSuccess(false);
//            String msg = "Operation 'ballerinaDocument/syntaxTreeModify' failed!";
//            logError(msg, e, request.getDocumentIdentifier(), (Position) null);
        }
        return CompletableFuture.supplyAsync(() -> reply);
    }

    @Override
    public CompletableFuture<BallerinaASTResponse> astModify(BallerinaASTModifyRequest request) {
        BallerinaASTResponse reply = new BallerinaASTResponse();
//        String fileUri = request.getDocumentIdentifier().getUri();
//
//        Optional<Path> filePath = CommonUtil.getPathFromURI(fileUri);
//        if (!filePath.isPresent()) {
//            return CompletableFuture.supplyAsync(() -> reply);
//        }
//        Path compilationPath = getUntitledFilePath(filePath.get().toString()).orElse(filePath.get());
//        Optional<Lock> lock = documentManager.lockFile(compilationPath);
//        String oldContent = "";
//        try {
//            oldContent = documentManager.getFileContent(compilationPath);
//            LSContext astContext = BallerinaTreeModifyUtil.modifyTree(request.getAstModifications(),
//                    fileUri, compilationPath, documentManager);
//            LSModuleCompiler.getBLangPackage(astContext, this.documentManager, false, false);
//            reply.setSource(astContext.get(UPDATED_SOURCE));
//            reply.setAst(getTreeForContent(astContext));
//            reply.setParseSuccess(isParseSuccess(astContext));
//        } catch (Throwable e) {
//            reply.setParseSuccess(false);
//            String msg = "Operation 'ballerinaDocument/ast' failed!";
//            logError(msg, e, request.getDocumentIdentifier(), (Position) null);
//        } finally {
//            if (!reply.isParseSuccess()) {
//                try {
//                    TextDocumentContentChangeEvent changeEvent = new TextDocumentContentChangeEvent(oldContent);
//                    documentManager.updateFile(compilationPath, Collections.singletonList(changeEvent));
//                } catch (WorkspaceDocumentException e) {
//                    logError("Failed to revert file content.", e, request.getDocumentIdentifier(),
//                            (Position) null);
//                }
//            }
//            lock.ifPresent(Lock::unlock);
//        }
        return CompletableFuture.supplyAsync(() -> reply);
    }

    @Override
    public CompletableFuture<BallerinaSyntaxTreeResponse> triggerModify(BallerinaTriggerModifyRequest request) {
        BallerinaSyntaxTreeResponse reply = new BallerinaSyntaxTreeResponse();
//        String fileUri = request.getDocumentIdentifier().getUri();
//
//        Optional<Path> filePath = CommonUtil.getPathFromURI(fileUri);
//        if (!filePath.isPresent()) {
//            return CompletableFuture.supplyAsync(() -> reply);
//        }
//        Path compilationPath = getUntitledFilePath(filePath.get().toString()).orElse(filePath.get());
//        Optional<Lock> lock = documentManager.lockFile(compilationPath);
//        String oldContent = "";
//        try {
//            // Apply modifications to the trigger
//            oldContent = documentManager.getFileContent(compilationPath);
//            LSContext astContext = BallerinaTriggerModifyUtil.modifyTrigger(request.getType(), request.getConfig(),
//                    fileUri, compilationPath, documentManager);
//
//            // Get the BLang Package.
//            BLangPackage bLangPackage = LSModuleCompiler.getBLangPackage(astContext, this.documentManager, true,
//                    false);
//
//            // Get the Visible endpoints.
//            CompilerContext compilerContext = astContext.get(DocumentServiceKeys.COMPILER_CONTEXT_KEY);
//            VisibleEndpointVisitor visibleEndpointVisitor = new VisibleEndpointVisitor(compilerContext);
//            bLangPackage.accept(visibleEndpointVisitor);
//            Map<BLangNode, List<SymbolMetaInfo>> visibleEPsByNode = visibleEndpointVisitor.getVisibleEPsByNode();
//
//            // Get the syntax tree for the document.
//            TextDocument textDocument = documentManager.getTree(compilationPath).textDocument();
//            SyntaxTree syntaxTree = SyntaxTree.from(textDocument, compilationPath.toString());
//
//            // Get the generated syntax tree JSON with type info.
//            JsonElement jsonSyntaxTree = TextDocumentFormatUtil.getSyntaxTreeJSON(syntaxTree, bLangPackage,
//                    visibleEPsByNode);
//
//            // Preparing the response.
//            reply.setSource(syntaxTree.toSourceCode());
//            reply.setSyntaxTree(jsonSyntaxTree);
//            reply.setParseSuccess(isParseSuccess(astContext));
//        } catch (Throwable e) {
//            reply.setParseSuccess(false);
//            String msg = "Operation 'ballerinaDocument/ast' failed!";
//            logError(msg, e, request.getDocumentIdentifier(), (Position) null);
//        } finally {
//            if (!reply.isParseSuccess()) {
//                try {
//                    TextDocumentContentChangeEvent changeEvent = new TextDocumentContentChangeEvent(oldContent);
//                    documentManager.updateFile(compilationPath, Collections.singletonList(changeEvent));
//                } catch (WorkspaceDocumentException e) {
//                    logError("Failed to revert file content.", e, request.getDocumentIdentifier(),
//                            (Position) null);
//                }
//            }
//            lock.ifPresent(Lock::unlock);
//        }
        return CompletableFuture.supplyAsync(() -> reply);
    }

    @Override
    public CompletableFuture<BallerinaASTDidChangeResponse> astDidChange(BallerinaASTDidChange notification) {
        BallerinaASTDidChangeResponse reply = new BallerinaASTDidChangeResponse();
//        String fileUri = notification.getTextDocumentIdentifier().getUri();
//        Optional<Path> filePath = CommonUtil.getPathFromURI(fileUri);
//        if (!filePath.isPresent()) {
//            return CompletableFuture.supplyAsync(() -> reply);
//        }
//        Path compilationPath = getUntitledFilePath(filePath.get().toString()).orElse(filePath.get());
//        Optional<Lock> lock = documentManager.lockFile(compilationPath);
//        try {
//            // calculate range to replace
//            String fileContent = documentManager.getFileContent(compilationPath);
//            String[] contentComponents = fileContent.split("\\n|\\r\\n|\\r");
//            int lastNewLineCharIndex = Math.max(fileContent.lastIndexOf("\n"), fileContent.lastIndexOf("\r"));
//            int lastCharCol = fileContent.substring(lastNewLineCharIndex + 1).length();
//            int totalLines = contentComponents.length;
//            Range range = new Range(new Position(0, 0), new Position(totalLines, lastCharCol));
//
//            // generate source for the new ast.
//            JsonObject ast = notification.getAst();
//            FormattingSourceGen.build(ast, "CompilationUnit");
//            // we are reformatting entire document upon each astChange
//            // until partial formatting is supported
//            // FormattingVisitorEntry formattingUtil = new FormattingVisitorEntry();
//            // formattingUtil.accept(ast);
//            String textEditContent = FormattingSourceGen.getSourceOf(ast);
//
//            // create text edit
//            TextEdit textEdit = new TextEdit(range, textEditContent);
//            ApplyWorkspaceEditParams applyWorkspaceEditParams = new ApplyWorkspaceEditParams();
//            TextDocumentEdit txtDocumentEdit = new TextDocumentEdit(notification.getTextDocumentIdentifier(),
//                    Collections.singletonList(textEdit));
//
//            WorkspaceEdit workspaceEdit = new WorkspaceEdit(Collections.singletonList(
//                    Either.forLeft(txtDocumentEdit)));
//            applyWorkspaceEditParams.setEdit(workspaceEdit);
//
//            // update the document
//            ballerinaLanguageServer.getClient().applyEdit(applyWorkspaceEditParams);
//            reply.setContent(textEditContent);
//        } catch (Throwable e) {
//            String msg = "Operation 'ballerinaDocument/astDidChange' failed!";
//            logError(msg, e, notification.getTextDocumentIdentifier(), (Position) null);
//        } finally {
//            lock.ifPresent(Lock::unlock);
//        }
        return CompletableFuture.supplyAsync(() -> reply);
    }

    @Override
    public CompletableFuture<BallerinaProject> project(BallerinaProjectParams params) {
        return CompletableFuture.supplyAsync(() -> {
            BallerinaProject project = new BallerinaProject();
            try {
                Optional<Path> filePath = CommonUtil.getPathFromURI(params.getDocumentIdentifier().getUri());
                if (!filePath.isPresent()) {
                    return project;
                }
                project.setPath(getProjectDir(filePath.get()));
            } catch (Throwable e) {
                String msg = "Operation 'ballerinaDocument/project' failed!";
                logError(msg, e, params.getDocumentIdentifier(), (Position) null);
            }
            return project;
        });
    }

    private JsonElement getTreeForContent(LSContext context) throws JSONGenerationException {
        BLangPackage bLangPackage = context.get(DocumentServiceKeys.CURRENT_BLANG_PACKAGE_CONTEXT_KEY);
        CompilerContext compilerContext = context.get(DocumentServiceKeys.COMPILER_CONTEXT_KEY);
        VisibleEndpointVisitor visibleEndpointVisitor = new VisibleEndpointVisitor(compilerContext);

        if (bLangPackage.symbol != null) {
            visibleEndpointVisitor.visit(bLangPackage);
            Map<BLangNode, List<SymbolMetaInfo>> visibleEPsByNode = visibleEndpointVisitor.getVisibleEPsByNode();
            String relativeFilePath = context.get(DocumentServiceKeys.RELATIVE_FILE_PATH_KEY);
            BLangCompilationUnit compilationUnit = bLangPackage.getCompilationUnits().stream()
                    .filter(cUnit -> cUnit.getPosition().lineRange().filePath().replace("/", CommonUtil.FILE_SEPARATOR)
                            .equals(relativeFilePath))
                    .findFirst()
                    .orElse(null);
            JsonElement jsonAST = TextDocumentFormatUtil.generateJSON(compilationUnit, new HashMap<>(),
                    visibleEPsByNode);
            FormattingSourceGen.build(jsonAST.getAsJsonObject(), "CompilationUnit");
            return jsonAST;
        }
        return null;
    }

    /**
     * Util method to merge updated compilation unit to the current compilation unit.
     *
     * @param targetCompUnit    target compilation unit
     * @param generatedCompUnit generated compilation unit which needs to be merged
     */
    private void mergeAst(JsonObject targetCompUnit, JsonObject generatedCompUnit) {
        generatedCompUnit.getAsJsonArray("topLevelNodes").forEach(item -> {
            JsonObject topLevelNode = item.getAsJsonObject();
            if (topLevelNode.get("kind").getAsString().equals("Import")) {
                if (!hasImport(targetCompUnit, topLevelNode)) {
                    int startPosition = FormattingSourceGen.getStartPosition(targetCompUnit, "imports", -1);
                    FormattingSourceGen.reconcileWS(topLevelNode,
                            targetCompUnit.getAsJsonArray("topLevelNodes"), targetCompUnit, startPosition);
                    targetCompUnit.getAsJsonArray("topLevelNodes").add(topLevelNode);
                }
            }

            if (topLevelNode.get("kind").getAsString().equals("Service")) {
                for (JsonElement astNode : targetCompUnit.getAsJsonArray("topLevelNodes")) {
                    JsonObject targetNode = astNode.getAsJsonObject();
                    if (targetNode.get("kind").getAsString().equals("Service")) {
                        if (targetNode.get("name").getAsJsonObject().get("value")
                                .equals(topLevelNode.get("name").getAsJsonObject().get("value"))) {
                            mergeServices(targetNode, topLevelNode, targetCompUnit);
                        }
                    }
                }
            }
        });
    }

    /**
     * Util method to merge given two service nodes.
     *
     * @param originService Origin service
     * @param targetService Target service which will get merged to origin service
     */
    private void mergeServices(JsonObject originService, JsonObject targetService, JsonObject tree) {
        mergeAnnotations(originService, targetService, tree);
        List<JsonObject> targetServices = new ArrayList<>();

        for (JsonElement targetItem : targetService.getAsJsonArray("resources")) {
            JsonObject targetResource = targetItem.getAsJsonObject();
            boolean matched = false;
            for (JsonElement originItem : originService.getAsJsonArray("resources")) {
                JsonObject originResource = originItem.getAsJsonObject();
                if (matchResource(originResource, targetResource)) {
                    matched = true;
                    mergeAnnotations(originResource, targetResource, tree);
                }
            }

            if (!matched) {
                targetResource.getAsJsonObject("body").add("statements", new JsonArray());
                targetServices.add(targetResource);
            }
        }

        targetServices.forEach(resource -> {
            int startIndex = FormattingSourceGen.getStartPosition(originService, "resources", -1);
            FormattingSourceGen.reconcileWS(resource, originService.getAsJsonArray("resources"), tree,
                    startIndex);
            originService.getAsJsonArray("resources").add(resource);
        });
    }

    /**
     * Util method to merge annotation attachments.
     *
     * @param targetNode target node
     * @param sourceNode source node which will get merged to target node
     */
    private void mergeAnnotations(JsonObject targetNode, JsonObject sourceNode, JsonObject tree) {
        JsonArray annotationAttachments = sourceNode.has("annotationAttachments")
                ? sourceNode.getAsJsonArray("annotationAttachments")
                : sourceNode.getAsJsonArray("annAttachments");
        for (JsonElement item : annotationAttachments) {
            JsonObject sourceNodeAttachment = item.getAsJsonObject();

            JsonObject matchedTargetNode = findAttachmentNode(targetNode, sourceNodeAttachment);

            if (matchedTargetNode != null) {
                if (sourceNodeAttachment.getAsJsonObject("expression").get("kind").getAsString()
                        .equals("RecordLiteralExpr") && matchedTargetNode.getAsJsonObject("expression").get("kind")
                        .getAsString().equals("RecordLiteralExpr")) {

                    JsonObject sourceRecord = sourceNodeAttachment.getAsJsonObject("expression");
                    JsonObject matchedTargetRecord = matchedTargetNode.getAsJsonObject("expression");

                    if (sourceNodeAttachment.getAsJsonObject("annotationName").get("value").getAsString()
                            .equals("MultiResourceInfo")) {
                        JsonArray sourceResourceInformations = sourceRecord.getAsJsonArray("keyValuePairs")
                                .get(0).getAsJsonObject().getAsJsonObject("value").getAsJsonArray("keyValuePairs");
                        JsonArray targetResourceInformations = matchedTargetRecord.getAsJsonArray("keyValuePairs")
                                .get(0).getAsJsonObject().getAsJsonObject("value").getAsJsonArray("keyValuePairs");

                        // Get map values of the resourceInformation map in MultiResourceInfo annotation.
                        for (JsonElement sourceResourceInfoItem : sourceResourceInformations) {
                            JsonObject sourceResourceInfo = sourceResourceInfoItem.getAsJsonObject();
                            JsonObject matchedTargetResourceInfo = null;
                            for (JsonElement targetResourceInfoItem : targetResourceInformations) {
                                JsonObject targetResourceInfo = targetResourceInfoItem.getAsJsonObject();
                                if (targetResourceInfo.has("key")
                                        && targetResourceInfo.getAsJsonObject("key").get("kind").getAsString()
                                        .equals("Literal")) {
                                    JsonObject targetResourceInfoKey = targetResourceInfo.getAsJsonObject("key");
                                    JsonObject sourceResourceInfoKey = sourceResourceInfo.getAsJsonObject("key");

                                    if (sourceResourceInfoKey.get("value").getAsString()
                                            .equals(targetResourceInfoKey.get("value").getAsString())) {
                                        matchedTargetResourceInfo = targetResourceInfo;
                                    }
                                }
                            }

                            if (matchedTargetResourceInfo != null) {
                                JsonArray sourceResourceInfoOperation = sourceResourceInfo.getAsJsonObject("value")
                                        .getAsJsonArray("keyValuePairs");
                                JsonArray targetResourceInfoOperation = matchedTargetResourceInfo
                                        .getAsJsonObject("value").getAsJsonArray("keyValuePairs");

                                for (JsonElement keyValueItem : sourceResourceInfoOperation) {
                                    JsonObject sourceKeyValue = keyValueItem.getAsJsonObject();
                                    int matchedKeyValuePairIndex = 0;
                                    JsonObject matchedObj = null;
                                    for (JsonElement matchedKeyValueItem : targetResourceInfoOperation) {
                                        JsonObject matchedKeyValue = matchedKeyValueItem.getAsJsonObject();
                                        if ((matchedKeyValue.has("key") &&
                                                matchedKeyValue.getAsJsonObject("key").get("kind").getAsString()
                                                        .equals("SimpleVariableRef"))) {
                                            JsonObject matchedKey = matchedKeyValue.getAsJsonObject("key");
                                            JsonObject sourceKey = sourceKeyValue.getAsJsonObject("key");
                                            if (matchedKey.getAsJsonObject("variableName").get("value").getAsString()
                                                    .equals(sourceKey.getAsJsonObject("variableName").get("value")
                                                            .getAsString())) {
                                                matchedObj = matchedKeyValue;
                                                break;
                                            }
                                        }
                                        matchedKeyValuePairIndex++;
                                    }

                                    if (matchedObj != null) {
                                        List<JsonObject> matchedObjWS = FormattingSourceGen.extractWS(matchedObj);
                                        int firstTokenIndex = matchedObjWS.get(0).get("i").getAsInt();
                                        targetResourceInfoOperation
                                                .remove(matchedKeyValuePairIndex);
                                        FormattingSourceGen.reconcileWS(sourceKeyValue, targetResourceInfoOperation,
                                                tree, firstTokenIndex);
                                        targetResourceInfoOperation.add(sourceKeyValue);
                                    } else {
                                        // Add new key value pair to the annotation record.
                                        FormattingSourceGen.reconcileWS(sourceKeyValue, targetResourceInfoOperation,
                                                tree, -1);
                                        targetResourceInfoOperation.add(sourceKeyValue);

                                        if (targetResourceInfoOperation.size() > 1) {
                                            // Add a new comma to separate the new key value pair.
                                            int startIndex = FormattingSourceGen.extractWS(sourceKeyValue).get(0)
                                                    .getAsJsonObject().get("i").getAsInt();
                                            FormattingSourceGen.addNewWS(matchedTargetResourceInfo
                                                    .getAsJsonObject("value"), tree, "", ",", true, startIndex);
                                        }
                                    }
                                }

                            } else {
                                FormattingSourceGen.reconcileWS(sourceResourceInfo, targetResourceInformations,
                                        tree, -1);
                                targetResourceInformations.add(sourceResourceInfo);
                            }
                        }

                    } else {
                        for (JsonElement keyValueItem : sourceRecord.getAsJsonArray("keyValuePairs")) {
                            JsonObject sourceKeyValue = keyValueItem.getAsJsonObject();
                            int matchedKeyValuePairIndex = 0;
                            JsonObject matchedObj = null;

                            for (JsonElement matchedKeyValueItem :
                                    matchedTargetRecord.getAsJsonArray("keyValuePairs")) {
                                JsonObject matchedKeyValue = matchedKeyValueItem.getAsJsonObject();
                                if ((matchedKeyValue.has("key") &&
                                        matchedKeyValue.getAsJsonObject("key").get("kind").getAsString()
                                                .equals("SimpleVariableRef"))) {
                                    JsonObject matchedKey = matchedKeyValue.getAsJsonObject("key");
                                    JsonObject sourceKey = sourceKeyValue.getAsJsonObject("key");
                                    if (matchedKey.getAsJsonObject("variableName").get("value").getAsString()
                                            .equals(sourceKey.getAsJsonObject("variableName").get("value")
                                                    .getAsString())) {
                                        matchedObj = matchedKeyValue;
                                        break;
                                    }
                                }
                                matchedKeyValuePairIndex++;
                            }

                            if (matchedObj != null) {
                                List<JsonObject> matchedObjWS = FormattingSourceGen.extractWS(matchedObj);
                                int firstTokenIndex = matchedObjWS.get(0).get("i").getAsInt();
                                matchedTargetRecord.getAsJsonArray("keyValuePairs")
                                        .remove(matchedKeyValuePairIndex);
                                FormattingSourceGen.reconcileWS(sourceKeyValue, matchedTargetRecord
                                        .getAsJsonArray("keyValuePairs"), tree, firstTokenIndex);
                                matchedTargetRecord.getAsJsonArray("keyValuePairs").add(sourceKeyValue);
                            } else {
                                // Add the new record key value pair.
                                FormattingSourceGen.reconcileWS(sourceKeyValue, matchedTargetRecord
                                        .getAsJsonArray("keyValuePairs"), tree, -1);
                                matchedTargetRecord.getAsJsonArray("keyValuePairs").add(sourceKeyValue);

                                if (matchedTargetRecord.getAsJsonArray("keyValuePairs").size() > 1) {
                                    // Add a new comma to separate the new key value pair.
                                    int startIndex = FormattingSourceGen.extractWS(sourceKeyValue).get(0)
                                            .getAsJsonObject().get("i").getAsInt();
                                    FormattingSourceGen.addNewWS(matchedTargetRecord, tree, "", ",", true,
                                            startIndex);
                                }
                            }
                        }
                    }
                }
            } else {
                int startIndex = FormattingSourceGen.getStartPosition(targetNode, "annAttachments", -1);
                JsonArray targetAnnAttachments = targetNode.has("annotationAttachments")
                        ? targetNode.getAsJsonArray("annotationAttachments")
                        : targetNode.getAsJsonArray("annAttachments");
                FormattingSourceGen.reconcileWS(sourceNodeAttachment, targetAnnAttachments, tree, startIndex);
                targetAnnAttachments.add(sourceNodeAttachment);
            }

        }
    }

    private JsonObject findAttachmentNode(JsonObject targetNode,
                                          JsonObject sourceNodeAttachment) {
        JsonObject matchedNode = null;
        JsonArray annotationAttachments = targetNode.has("annotationAttachments")
                ? targetNode.getAsJsonArray("annotationAttachments")
                : targetNode.getAsJsonArray("annAttachments");
        for (JsonElement item : annotationAttachments) {
            JsonObject attachmentNode = item.getAsJsonObject();
            if (sourceNodeAttachment.getAsJsonObject("annotationName").get("value").getAsString()
                    .equals(attachmentNode.getAsJsonObject("annotationName").get("value").getAsString())
                    && sourceNodeAttachment.getAsJsonObject("packageAlias").get("value").getAsString()
                    .equals(attachmentNode.getAsJsonObject("packageAlias").get("value").getAsString())) {
                matchedNode = attachmentNode;
                break;
            }
        }
        return matchedNode;
    }

    /**
     * Util method to match given resource in a service node.
     *
     * @param astResource     service node
     * @param openApiResource resource which needs to be checked
     * @return true if matched else false
     */
    private boolean matchResource(JsonObject astResource, JsonObject openApiResource) {
        return astResource.getAsJsonObject("name").get("value").getAsString()
                .equals(openApiResource.getAsJsonObject("name").get("value").getAsString());
    }

    /**
     * Util method to check if given node is an existing import in current AST model.
     *
     * @param originAst    - current AST model
     * @param mergePackage - Import Node
     * @return - boolean status
     */
    private boolean hasImport(JsonObject originAst, JsonObject mergePackage) {
        boolean importFound = false;

        for (JsonElement node : originAst.getAsJsonArray("topLevelNodes")) {
            JsonObject originNode = node.getAsJsonObject();
            if (importFound) {
                break;
            } else if (originNode.get("kind").getAsString().equals("Import")
                    && originNode.get("orgName").getAsJsonObject().get("value").getAsString()
                    .equals(mergePackage.get("orgName").getAsJsonObject().get("value").getAsString())
                    && originNode.getAsJsonArray("packageName").size() == mergePackage
                    .getAsJsonArray("packageName").size()) {
                JsonArray packageName = originNode.getAsJsonArray("packageName");
                for (int i = 0; i < packageName.size(); i++) {
                    JsonArray mergePackageName = mergePackage.getAsJsonArray("packageName");
                    if (mergePackageName.get(i).getAsJsonObject().get("value").getAsString()
                            .equals(packageName.get(i).getAsJsonObject().get("value").getAsString())) {
                        importFound = true;
                    } else {
                        importFound = false;
                        break;
                    }
                }
            }
        }

        return importFound;
    }

}
