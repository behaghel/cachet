export type RequestPackOptions = { policyId: string; purpose: string };
export type VerifyResult = { badge: string; predicates: string[]; freshness: string };

export async function listPacks(base = "http://localhost:8081"): Promise<{id:string;version:string;name:string}[]> {
  const res = await fetch(`${base}/packs`);
  return res.json();
}

export async function requestPack(opts: RequestPackOptions): Promise<string> {
  return `cachet://present?policyId=${encodeURIComponent(opts.policyId)}&purpose=${encodeURIComponent(opts.purpose)}`;
}

export async function verifyPresentation(bundle: any, policyId: string, base = "http://localhost:8081"): Promise<VerifyResult> {
  const res = await fetch(`${base}/presentations/verify`, {
    method: 'POST', headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ policyId, bundle })
  });
  return res.json();
}
