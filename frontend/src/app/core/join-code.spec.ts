import { extractCode } from './join-code';

describe('extractCode', () => {
  it('uppercases a bare code and drops stray characters', () => {
    expect(extractCode('x7k-2p9')).toBe('X7K2P9');
  });

  it('takes the code from a full room link', () => {
    expect(extractCode('https://quickdot.amartian.ca/r/X7K2P9')).toBe('X7K2P9');
    expect(extractCode('quickdot.amartian.ca/r/ab2cd3?x=1')).toBe('AB2CD3');
  });

  it('caps a typed code at six characters', () => {
    expect(extractCode('ABCDEFGH')).toBe('ABCDEF');
  });
});
