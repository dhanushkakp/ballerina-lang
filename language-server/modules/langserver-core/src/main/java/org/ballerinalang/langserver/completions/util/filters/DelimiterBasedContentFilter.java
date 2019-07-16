/*
*  Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*
*  WSO2 Inc. licenses this file to you under the Apache License,
*  Version 2.0 (the "License"); you may not use this file except
*  in compliance with the License.
*  You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
*  Unless required by applicable law or agreed to in writing,
*  software distributed under the License is distributed on an
*  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
*  KIND, either express or implied.  See the License for the
*  specific language governing permissions and limitations
*  under the License.
*/
package org.ballerinalang.langserver.completions.util.filters;

import org.antlr.v4.runtime.CommonToken;
import org.antlr.v4.runtime.Token;
import org.ballerinalang.langserver.LSGlobalContextKeys;
import org.ballerinalang.langserver.common.CommonKeys;
import org.ballerinalang.langserver.common.utils.CommonUtil;
import org.ballerinalang.langserver.common.utils.FilterUtils;
import org.ballerinalang.langserver.compiler.DocumentServiceKeys;
import org.ballerinalang.langserver.compiler.LSContext;
import org.ballerinalang.langserver.completions.CompletionKeys;
import org.ballerinalang.langserver.completions.SymbolInfo;
import org.ballerinalang.langserver.index.LSIndexException;
import org.ballerinalang.langserver.index.LSIndexImpl;
import org.ballerinalang.langserver.index.dao.BPackageSymbolDAO;
import org.ballerinalang.langserver.index.dao.DAOType;
import org.ballerinalang.langserver.index.dto.BFunctionSymbolDTO;
import org.ballerinalang.langserver.index.dto.BObjectTypeSymbolDTO;
import org.ballerinalang.langserver.index.dto.BPackageSymbolDTO;
import org.ballerinalang.langserver.index.dto.BRecordTypeSymbolDTO;
import org.ballerinalang.langserver.index.dto.OtherTypeSymbolDTO;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.ballerinalang.compiler.parser.antlr4.BallerinaParser;
import org.wso2.ballerinalang.compiler.tree.BLangImportPackage;
import org.wso2.ballerinalang.compiler.tree.BLangPackage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Filter the actions, functions and types in a package.
 */
public class DelimiterBasedContentFilter extends AbstractSymbolFilter {

    private static final Logger logger = LoggerFactory.getLogger(DelimiterBasedContentFilter.class);

    @Override
    public Either<List<CompletionItem>, List<SymbolInfo>> filterItems(LSContext ctx) {
        List<CommonToken> lhsTokens = ctx.get(CompletionKeys.LHS_TOKENS_KEY);
        List<CommonToken> defaultTokens = lhsTokens.stream()
                .filter(commonToken -> commonToken.getChannel() == Token.DEFAULT_CHANNEL)
                .collect(Collectors.toList());
        List<Integer> defaultTokenTypes = defaultTokens.stream()
                .map(CommonToken::getType)
                .collect(Collectors.toList());
        int delimiter = ctx.get(CompletionKeys.INVOCATION_TOKEN_TYPE_KEY);
        String symbolToken = defaultTokens.get(defaultTokenTypes.lastIndexOf(delimiter) - 1).getText();
        ArrayList<SymbolInfo> returnSymbolsInfoList = new ArrayList<>();
        List<SymbolInfo> visibleSymbols = ctx.get(CommonKeys.VISIBLE_SYMBOLS_KEY);
        SymbolInfo symbol = FilterUtils.getVariableByName(symbolToken, visibleSymbols);
        boolean isActionInvocation = BallerinaParser.RARROW == delimiter
                && CommonUtil.isClientObject(symbol.getScopeEntry().symbol);
        boolean isWorkerSend = !isActionInvocation && BallerinaParser.RARROW == delimiter;

        if (BallerinaParser.DOT == delimiter || BallerinaParser.NOT == delimiter || isActionInvocation) {
            returnSymbolsInfoList.addAll(FilterUtils.filterVariableEntriesOnDelimiter(ctx, symbolToken, delimiter,
                    defaultTokens, defaultTokenTypes.lastIndexOf(delimiter)));
        } else if (isWorkerSend) {
            returnSymbolsInfoList.addAll(CommonUtil.getWorkerSymbols(ctx));
        } else if (BallerinaParser.COLON == delimiter) {
            // We are filtering the package functions, actions and the types
            Either<List<CompletionItem>, List<SymbolInfo>> filteredList = this.getActionsFunctionsAndTypes(ctx,
                    symbolToken, delimiter, defaultTokens, defaultTokenTypes.lastIndexOf(delimiter));
            if (filteredList.isLeft()) {
                return Either.forLeft(filteredList.getLeft());
            }
            returnSymbolsInfoList.addAll(filteredList.getRight());
        }

        return Either.forRight(returnSymbolsInfoList);
    }

    /**
     * Get the actions, functions and types.
     * @param context               Text Document Service context (Completion Context)
     * @param pkgName               Package name to evaluate against
     * @param delimiter             Delimiter
     * @return {@link ArrayList}    List of filtered symbol info
     */
    private Either<List<CompletionItem>, List<SymbolInfo>> getActionsFunctionsAndTypes(
            LSContext context, String pkgName, int delimiter, List<CommonToken> defaultTokens, int delimIndex) {

        LSIndexImpl lsIndex = context.get(LSGlobalContextKeys.LS_INDEX_KEY);
        // Extract the package symbol
        String relativePath = context.get(DocumentServiceKeys.RELATIVE_FILE_PATH_KEY);
        BLangPackage currentBLangPkg = context.get(DocumentServiceKeys.CURRENT_BLANG_PACKAGE_CONTEXT_KEY);
        BLangPackage sourceOwnerPkg = CommonUtil.getSourceOwnerBLangPackage(relativePath, currentBLangPkg);
        Optional bLangImport = CommonUtil.getCurrentFileImports(sourceOwnerPkg, context)
                .stream()
                .filter(importPkg -> importPkg.getAlias().getValue().equals(pkgName))
                .findFirst();
        
        try {
            String realPackageName;
            String realOrgName;
            if (bLangImport.isPresent()) {
                // There is an added import statement.
                realPackageName = CommonUtil.getPackageNameComponentsCombined(((BLangImportPackage) bLangImport.get()));
                realOrgName = ((BLangImportPackage) bLangImport.get()).getOrgName().getValue();
            } else {
                realPackageName = pkgName;
                realOrgName = "";
            }
            
            BPackageSymbolDTO dto = new BPackageSymbolDTO.BPackageSymbolDTOBuilder()
                    .setName(realPackageName)
                    .setOrgName(realOrgName)
                    .build();
            List<BPackageSymbolDTO> result = ((BPackageSymbolDAO) lsIndex.getDaoFactory().get(DAOType.PACKAGE_SYMBOL))
                    .get(dto);
            
            if (result.isEmpty()) {
                // There is no package entry found in the index.
                return this.filterSymbolsOnFallback(context, pkgName, delimiter, defaultTokens, delimIndex);
            } else {
                // Package entry found in the index. content is searched LSIndex.
                HashMap<Integer, ArrayList<CompletionItem>> completionMap = new HashMap<>();

                BPackageSymbolDAO pkgSymbolDAO = ((BPackageSymbolDAO) lsIndex.getDaoFactory()
                        .get(DAOType.PACKAGE_SYMBOL));
                ArrayList<BFunctionSymbolDTO> funcDTOs = new ArrayList<>(pkgSymbolDAO.getFunctions(dto, -1, false,
                        false, false));
                ArrayList<BRecordTypeSymbolDTO> recordDTOs = new ArrayList<>(pkgSymbolDAO.getRecords(dto, false));
                ArrayList<OtherTypeSymbolDTO> otherTypeDTOs = new ArrayList<>(pkgSymbolDAO.getOtherTypes(dto));
                ArrayList<BObjectTypeSymbolDTO> objDTOs = new ArrayList<>(pkgSymbolDAO.getObjects(dto, false));
                ArrayList<BObjectTypeSymbolDTO> clientEpDTOs = new ArrayList<>(pkgSymbolDAO.getClientEndpoints(dto));

                if (bLangImport.isPresent()) {
                    List<CompletionItem> completionItems = funcDTOs.stream()
                            .map(BFunctionSymbolDTO::getCompletionItem)
                            .collect(Collectors.toList());
                    completionItems.addAll(recordDTOs.stream()
                            .map(BRecordTypeSymbolDTO::getCompletionItem)
                            .collect(Collectors.toList()));
                    completionItems.addAll(objDTOs.stream()
                            .map(BObjectTypeSymbolDTO::getCompletionItem)
                            .collect(Collectors.toList()));
                    completionItems.addAll(clientEpDTOs.stream()
                            .map(BObjectTypeSymbolDTO::getCompletionItem)
                            .collect(Collectors.toList()));
                    completionItems.addAll(otherTypeDTOs.stream()
                            .map(OtherTypeSymbolDTO::getCompletionItem)
                            .collect(Collectors.toList()));
                    
                    return Either.forLeft(completionItems);
                }
                
                funcDTOs.forEach(fDto ->
                        CommonUtil.populateIdCompletionMap(completionMap, fDto.getPackageId(),
                                fDto.getCompletionItem()));
                recordDTOs.forEach(rDto ->
                        CommonUtil.populateIdCompletionMap(completionMap, rDto.getPackageId(),
                                rDto.getCompletionItem()));
                objDTOs.forEach(objDto ->
                        CommonUtil.populateIdCompletionMap(completionMap, objDto.getPackageId(),
                                objDto.getCompletionItem()));
                otherTypeDTOs.forEach(otherDto ->
                        CommonUtil.populateIdCompletionMap(completionMap, otherDto.getPackageId(),
                                otherDto.getCompletionItem()));
                
                return Either.forLeft(CommonUtil.fillCompletionWithPkgImport(completionMap, context));
            }
        } catch (LSIndexException e) {
            logger.warn("Error retrieving Completion Items from Index DB.");
            return this.filterSymbolsOnFallback(context, pkgName, delimiter, defaultTokens, delimIndex);
        }
    }
    
    private Either<List<CompletionItem>, List<SymbolInfo>> filterSymbolsOnFallback(LSContext context,
                                                                                   String pkgName, int delimiter,
                                                                                   List<CommonToken> defaultTokens,
                                                                                   int delimIndex) {
        List<SymbolInfo> filteredSymbols = FilterUtils.filterVariableEntriesOnDelimiter(context, pkgName, delimiter,
                defaultTokens, delimIndex);
        return Either.forRight(filteredSymbols);
    }
}
