export type RequestPackOptions = { policyId: string; purpose: string };
export type CacheResult = { cachet: string; predicates: string[]; freshness: string };

export async function listPacks(base = "http://localhost:8081"): Promise<{id:string;version:string;name:string}[]> {
  const res = await fetch(`${base}/packs`);
  return res.json();
}

export async function requestPack(opts: RequestPackOptions): Promise<string> {
  return `cachet://present?policyId=${encodeURIComponent(opts.policyId)}&purpose=${encodeURIComponent(opts.purpose)}`;
}

export async function cachePresentation(bundle: any, policyId: string, base = "http://localhost:8081"): Promise<CacheResult> {
  const res = await fetch(`${base}/presentations/cache`, {
    method: 'POST', headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ policyId, bundle })
  });
  return res.json();
}
