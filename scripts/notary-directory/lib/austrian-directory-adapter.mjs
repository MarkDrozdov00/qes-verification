const text = (html, field) => html.match(new RegExp(`data-field=["']${field}["'][^>]*>([^<]*)<`, 'i'))?.[1]?.trim() ?? null

export class AustrianDirectoryAdapter {
  parse(html) {
    const blocks = [...html.matchAll(/<article[^>]*data-notary-record[^>]*>([\s\S]*?)<\/article>/gi)].map((match) => match[1])
    if (!blocks.length) throw new Error('Expected notary result selector was not found; source markup may have changed')
    return blocks.map((block) => ({
      fullName: text(block, 'fullName'), givenName: text(block, 'givenName'), surname: text(block, 'surname'),
      professionalTitle: text(block, 'professionalTitle'), countryCode: text(block, 'countryCode') || 'AT',
      nCode: text(block, 'nCode'), registryStatus: text(block, 'registryStatus') || 'UNKNOWN',
      authorizations: [{ type: text(block, 'authorizationType') || 'NOTARIAL_SIGNATURE', validFrom: text(block, 'validFrom'), validUntil: text(block, 'validUntil') }],
      certificateReferences: [], sourceRecordUrl: text(block, 'sourceRecordUrl'),
    }))
  }

  async search() {
    throw new Error('Automated collection is disabled: use --mode manual unless source access permission is confirmed')
  }
}
