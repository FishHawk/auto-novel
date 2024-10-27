export namespace CCUtil {
  type Locale = 'zh-cn' | 'zh-tw';

  export type Converter = {
    toView: (text: string) => string;
    toData: (text: string) => string;
  };

  export const defaultConverter: Converter = {
    toView: (text: string) => text,
    toData: (text: string) => text,
  };

  export const createConverter = async (locale: Locale): Promise<Converter> => {
    if (locale === 'zh-cn') {
      return defaultConverter;
    } else if (locale === 'zh-tw') {
      const opencc: any = await import('opencc-js');
      const ccLocale = opencc.Locale;
      const customDict = [
        ['託', '托'],
        ['孃', '娘'],
      ];
      return {
        toView: opencc.ConverterFactory(ccLocale.from.cn, ccLocale.to.tw, [
          customDict,
        ]),
        toData: opencc.ConverterFactory(ccLocale.from.tw, ccLocale.to.cn),
      };
    }
    return locale satisfies never;
  };
}
