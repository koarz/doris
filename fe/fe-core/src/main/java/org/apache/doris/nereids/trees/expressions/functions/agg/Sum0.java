// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

package org.apache.doris.nereids.trees.expressions.functions.agg;

import org.apache.doris.catalog.FunctionSignature;
import org.apache.doris.nereids.exceptions.AnalysisException;
import org.apache.doris.nereids.trees.expressions.Expression;
import org.apache.doris.nereids.trees.expressions.functions.ComputePrecisionForSum;
import org.apache.doris.nereids.trees.expressions.functions.ExplicitlyCastableSignature;
import org.apache.doris.nereids.trees.expressions.functions.Function;
import org.apache.doris.nereids.trees.expressions.functions.window.SupportWindowAnalytic;
import org.apache.doris.nereids.trees.expressions.literal.BigIntLiteral;
import org.apache.doris.nereids.trees.expressions.literal.DecimalV3Literal;
import org.apache.doris.nereids.trees.expressions.literal.DoubleLiteral;
import org.apache.doris.nereids.trees.expressions.literal.LargeIntLiteral;
import org.apache.doris.nereids.trees.expressions.shape.UnaryExpression;
import org.apache.doris.nereids.trees.expressions.visitor.ExpressionVisitor;
import org.apache.doris.nereids.types.BigIntType;
import org.apache.doris.nereids.types.BooleanType;
import org.apache.doris.nereids.types.DataType;
import org.apache.doris.nereids.types.DecimalV2Type;
import org.apache.doris.nereids.types.DecimalV3Type;
import org.apache.doris.nereids.types.DoubleType;
import org.apache.doris.nereids.types.FloatType;
import org.apache.doris.nereids.types.IntegerType;
import org.apache.doris.nereids.types.LargeIntType;
import org.apache.doris.nereids.types.NullType;
import org.apache.doris.nereids.types.SmallIntType;
import org.apache.doris.nereids.types.TinyIntType;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;

/**
 * AggregateFunction 'sum0'. sum0 returns the sum of the values which go into it like sum.
 * It differs in that when no non null values are applied zero is returned instead of null.
 */
public class Sum0 extends NotNullableAggregateFunction
        implements UnaryExpression, ExplicitlyCastableSignature, ComputePrecisionForSum,
        SupportWindowAnalytic, RollUpTrait, SupportMultiDistinct {

    public static final List<FunctionSignature> SIGNATURES = ImmutableList.of(
            FunctionSignature.ret(DoubleType.INSTANCE).args(DoubleType.INSTANCE),
            FunctionSignature.ret(DoubleType.INSTANCE).args(FloatType.INSTANCE),
            FunctionSignature.ret(DecimalV3Type.WILDCARD).args(DecimalV3Type.WILDCARD),
            FunctionSignature.ret(LargeIntType.INSTANCE).args(LargeIntType.INSTANCE),
            FunctionSignature.ret(BigIntType.INSTANCE).args(BigIntType.INSTANCE),
            FunctionSignature.ret(BigIntType.INSTANCE).args(IntegerType.INSTANCE),
            FunctionSignature.ret(BigIntType.INSTANCE).args(SmallIntType.INSTANCE),
            FunctionSignature.ret(BigIntType.INSTANCE).args(TinyIntType.INSTANCE),
            FunctionSignature.ret(BigIntType.INSTANCE).args(BooleanType.INSTANCE)
    );

    /**
     * constructor with 1 argument.
     */
    public Sum0(Expression arg) {
        this(false, false, arg);
    }

    public Sum0(boolean distinct, Expression arg) {
        this(distinct, false, arg);
    }

    /**
     * constructor with 2 argument.
     */
    public Sum0(boolean distinct, boolean isSkew, Expression arg) {
        super("sum0", distinct, isSkew, arg);
    }

    @Override
    public MultiDistinctSum0 convertToMultiDistinct() {
        Preconditions.checkArgument(distinct,
                "can't convert to multi_distinct_sum because there is no distinct args");
        return new MultiDistinctSum0(false, child());
    }

    @Override
    public void checkLegalityBeforeTypeCoercion() {
        DataType argType = child().getDataType();
        if (!argType.isNumericType() && !argType.isBooleanType()
                && !argType.isNullType() && !argType.isStringLikeType()) {
            throw new AnalysisException("sum0 requires a numeric, boolean or string parameter: " + this.toSql());
        }
    }

    /**
     * withDistinctAndChildren.
     */
    @Override
    public Sum0 withDistinctAndChildren(boolean distinct, List<Expression> children) {
        Preconditions.checkArgument(children.size() == 1);
        return new Sum0(distinct, isSkew, children.get(0));
    }

    @Override
    public Expression withIsSkew(boolean isSkew) {
        return new Sum0(distinct, isSkew, child());
    }

    @Override
    public <R, C> R accept(ExpressionVisitor<R, C> visitor, C context) {
        return visitor.visitSum0(this, context);
    }

    @Override
    public List<FunctionSignature> getSignatures() {
        return SIGNATURES;
    }

    @Override
    public FunctionSignature searchSignature(List<FunctionSignature> signatures) {
        if (getArgument(0).getDataType() instanceof NullType) {
            return FunctionSignature.ret(BigIntType.INSTANCE).args(TinyIntType.INSTANCE);
        } else if (getArgument(0).getDataType() instanceof DecimalV2Type) {
            return FunctionSignature.ret(DecimalV3Type.WILDCARD).args(DecimalV3Type.WILDCARD);
        }
        return ExplicitlyCastableSignature.super.searchSignature(signatures);
    }

    @Override
    public Function constructRollUp(Expression param, Expression... varParams) {
        return new Sum0(this.distinct, isSkew, param);
    }

    @Override
    public boolean canRollUp() {
        return true;
    }

    @Override
    public Expression resultForEmptyInput() {
        DataType dataType = getDataType();
        if (dataType.isBigIntType()) {
            return new BigIntLiteral(0);
        } else if (dataType.isLargeIntType()) {
            return new LargeIntLiteral(new BigInteger("0"));
        } else if (dataType.isDecimalV3Type()) {
            return new DecimalV3Literal((DecimalV3Type) dataType, new BigDecimal("0"));
        } else if (dataType.isDoubleType()) {
            return new DoubleLiteral(0);
        } else {
            return new DoubleLiteral(0);
        }
    }
}
