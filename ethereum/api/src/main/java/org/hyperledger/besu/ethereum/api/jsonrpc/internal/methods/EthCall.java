/*
 * Copyright ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods;

import org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcErrorConverter;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.exception.InvalidJsonRpcParameters;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.BlockParameterOrBlockHash;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.JsonCallParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.transaction.TransactionSimulator;

public class EthCall extends AbstractBlockParameterOrBlockHashMethod {
  private final TransactionSimulator transactionSimulator;

  public EthCall(
      final BlockchainQueries blockchainQueries, final TransactionSimulator transactionSimulator) {
    super(blockchainQueries);
    this.transactionSimulator = transactionSimulator;
  }

  @Override
  public String getName() {
    return RpcMethod.ETH_CALL.getMethodName();
  }

  @Override
  protected BlockParameterOrBlockHash blockParameterOrBlockHash(
      final JsonRpcRequestContext request) {
    return request.getRequiredParameter(1, BlockParameterOrBlockHash.class);
  }

  @Override
  protected Object resultByBlockHash(final JsonRpcRequestContext request, final Hash blockHash) {
    final JsonCallParameter callParams = validateAndGetCallParams(request);

    return transactionSimulator
        .process(callParams, blockHash)
        .map(
            result ->
                result
                    .getValidationResult()
                    .either(
                        (() ->
                            new JsonRpcSuccessResponse(
                                request.getRequest().getId(), result.getOutput().toString())),
                        reason ->
                            new JsonRpcErrorResponse(
                                request.getRequest().getId(),
                                JsonRpcErrorConverter.convertTransactionInvalidReason(reason))))
        .orElse(validRequestBlockNotFound(request));
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    return (JsonRpcResponse) handleParamTypes(requestContext);
  }

  private JsonRpcSuccessResponse validRequestBlockNotFound(final JsonRpcRequestContext request) {
    return new JsonRpcSuccessResponse(request.getRequest().getId(), null);
  }

  private JsonCallParameter validateAndGetCallParams(final JsonRpcRequestContext request) {
    final JsonCallParameter callParams = request.getRequiredParameter(0, JsonCallParameter.class);
    if (callParams.getTo() == null) {
      throw new InvalidJsonRpcParameters("Missing \"to\" field in call arguments");
    }
    if (callParams.getGasPrice() != null
        && (callParams.getFeeCap().isPresent() || callParams.getGasPremium().isPresent())) {
      throw new InvalidJsonRpcParameters("gasPrice cannot be used with baseFee or feeCap");
    }
    return callParams;
  }
}
