export default async function handler(request, response) {
  if (request.method !== "POST") {
    response.setHeader("Allow", "POST");
    return response.status(405).json({ error: "Method not allowed" });
  }

  const webhookUrl = process.env.DISCORD_WEBHOOK_URL;
  if (!webhookUrl) {
    return response.status(500).json({ error: "Discord webhook is not configured" });
  }

  try {
    const discordResponse = await fetch(webhookUrl, {
      method: "POST",
      headers: {
        "Content-Type": "application/json"
      },
      body: JSON.stringify(request.body)
    });

    if (!discordResponse.ok) {
      return response.status(502).json({ error: "Discord webhook request failed" });
    }

    return response.status(204).end();
  } catch {
    return response.status(500).json({ error: "Discord reminder failed" });
  }
}
