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

#pragma once

#include <stdint.h>

#include "common/status.h"
#include "operator.h"

namespace doris {
#include "common/compile_check_begin.h"
class RuntimeState;

namespace pipeline {

class PartitionSortSourceOperatorX;
class PartitionSortSourceLocalState final
        : public PipelineXLocalState<PartitionSortNodeSharedState> {
public:
    ENABLE_FACTORY_CREATOR(PartitionSortSourceLocalState);
    using Base = PipelineXLocalState<PartitionSortNodeSharedState>;
    PartitionSortSourceLocalState(RuntimeState* state, OperatorXBase* parent)
            : PipelineXLocalState<PartitionSortNodeSharedState>(state, parent) {}

    Status init(RuntimeState* state, LocalStateInfo& info) override;

private:
    friend class PartitionSortSourceOperatorX;
    RuntimeProfile::Counter* _get_sorted_timer = nullptr;
    RuntimeProfile::Counter* _sorted_partition_output_rows_counter = nullptr;
    std::atomic<int> _sort_idx = 0;
};

class PartitionSortSourceOperatorX final : public OperatorX<PartitionSortSourceLocalState> {
public:
    using Base = OperatorX<PartitionSortSourceLocalState>;
    PartitionSortSourceOperatorX(ObjectPool* pool, const TPlanNode& tnode, int operator_id,
                                 const DescriptorTbl& descs)
            : OperatorX<PartitionSortSourceLocalState>(pool, tnode, operator_id, descs) {}
#ifdef BE_TEST
    PartitionSortSourceOperatorX() = default;
#endif
    Status get_block(RuntimeState* state, vectorized::Block* block, bool* eos) override;

    bool is_source() const override { return true; }

private:
    friend class PartitionSortSourceLocalState;
    Status get_sorted_block(RuntimeState* state, vectorized::Block* output_block,
                            PartitionSortSourceLocalState& local_state);
};

} // namespace pipeline
#include "common/compile_check_end.h"
} // namespace doris
