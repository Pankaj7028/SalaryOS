"use client";

import { useQuery } from "@tanstack/react-query";
import { analyticsKeys } from "@/lib/api/keys";
import {
  fetchCompaRatioDistribution,
  fetchHeadcount,
  fetchIncreaseCycle,
  fetchOutOfBand,
  fetchPayGap,
  fetchPayrollCost,
  type CompaRatioDistributionParams,
  type IncreaseCycleParams,
} from "@/lib/api/analytics";

export function usePayrollCost() {
  return useQuery({ queryKey: analyticsKeys.payrollCost(), queryFn: fetchPayrollCost });
}

export function useHeadcount() {
  return useQuery({ queryKey: analyticsKeys.headcount(), queryFn: fetchHeadcount });
}

export function useOutOfBand() {
  return useQuery({ queryKey: analyticsKeys.outOfBand(), queryFn: fetchOutOfBand });
}

export function useCompaRatioDistribution(params: CompaRatioDistributionParams) {
  return useQuery({
    queryKey: analyticsKeys.compaRatioDistribution(params),
    queryFn: () => fetchCompaRatioDistribution(params),
  });
}

export function usePayGap() {
  return useQuery({ queryKey: analyticsKeys.payGap(), queryFn: fetchPayGap });
}

export function useIncreaseCycle(params: IncreaseCycleParams) {
  return useQuery({
    queryKey: analyticsKeys.increaseCycle(params),
    queryFn: () => fetchIncreaseCycle(params),
  });
}
