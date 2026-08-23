"use client";

import { useQuery } from "@tanstack/react-query";
import { analyticsKeys } from "@/lib/api/keys";
import {
  fetchCompaRatioDistribution,
  fetchBandHealth,
  fetchDataHealth,
  fetchHeadcount,
  fetchIncreaseCycle,
  fetchOutOfBand,
  fetchPayGap,
  fetchPayrollCost,
  type AnalyticsBasis,
  type CompaRatioDistributionParams,
  type IncreaseCycleParams,
} from "@/lib/api/analytics";

export function usePayrollCost(basis: AnalyticsBasis = "BASE") {
  return useQuery({
    queryKey: analyticsKeys.payrollCost(basis),
    queryFn: () => fetchPayrollCost(basis),
    // Switching basis re-asks the same question a different way; keeping the old answer on screen
    // while the new one loads reads as a figure that is still valid, which it is not.
    placeholderData: undefined,
  });
}

export function useBandHealth() {
  return useQuery({ queryKey: analyticsKeys.bandHealth(), queryFn: fetchBandHealth });
}

export function useDataHealth() {
  return useQuery({ queryKey: analyticsKeys.dataHealth(), queryFn: fetchDataHealth });
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
