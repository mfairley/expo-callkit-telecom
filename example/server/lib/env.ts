import { z, type ZodError } from "zod";

export const apnsEnvSchema = z.object({
  BUNDLE_ID: z.string().min(1),
  APNS_KEY_ID: z.string().min(1),
  APNS_TEAM_ID: z.string().min(1),
  APNS_DEVICE_TOKEN: z.string().min(1),
});

export const fcmEnvSchema = z.object({
  FCM_TOKEN: z.string().min(1),
});

export type ApnsEnv = z.infer<typeof apnsEnvSchema>;
export type FcmEnv = z.infer<typeof fcmEnvSchema>;

export function parseEnv<T extends z.ZodTypeAny>(
  schema: T,
  label: string,
): z.infer<T> {
  const result = schema.safeParse(process.env);
  if (!result.success) {
    throw new Error(`${label} env invalid:\n${formatZodError(result.error)}`);
  }
  return result.data;
}

function formatZodError(err: ZodError): string {
  return err.issues
    .map((i) => `  - ${i.path.join(".")}: ${i.message}`)
    .join("\n");
}
