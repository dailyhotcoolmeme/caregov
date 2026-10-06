import React from 'react';
import { Text, View, Pressable, TextInput, Keyboard, StyleSheet, useWindowDimensions } from 'react-native';
import Ionicons from '@expo/vector-icons/Ionicons';
import { Picker } from '@react-native-picker/picker';
export const colors = { ink: '#202C31', muted: '#637177', green: '#147D64', background: '#F8FBFA', line: '#D9E3E0', amber: '#9C6219', blue: '#316EA1', red: '#AD3838' };
export function Label({ children, muted, style }) { return <Text style={[s.text, muted && { color: colors.muted }, style]}>{children}</Text>; }
export function Heading({ children }) { return <Text accessibilityRole="header" style={s.heading}>{children}</Text>; }
export function Button({ title, icon, onPress, secondary, danger, disabled, testID }) {
  return <Pressable accessibilityRole="button" accessibilityLabel={title} testID={testID} disabled={disabled} onPress={() => { Keyboard.dismiss(); onPress(); }}
    style={({ pressed }) => [s.button, secondary && s.secondary, danger && { backgroundColor: colors.red }, disabled && { opacity: .45 }, pressed && { opacity: .7 }]}>
    {icon && <Ionicons name={icon} size={21} color={secondary ? colors.green : 'white'} />}
    <Text style={[s.buttonText, secondary && { color: colors.green }]}>{title}</Text>
  </Pressable>;
}
export function Field({ label, value, onChangeText, multiline, numeric, secure, editable = true, maxLength }) {
  return <View style={s.field}><Label style={s.fieldLabel}>{label}</Label><TextInput accessibilityLabel={label} testID={label} value={String(value ?? '')} onChangeText={onChangeText}
    editable={editable} secureTextEntry={secure} keyboardType={numeric ? 'number-pad' : 'default'} multiline={multiline} returnKeyType={multiline ? 'default' : 'done'} onSubmitEditing={multiline ? undefined : () => Keyboard.dismiss()} maxLength={maxLength || (multiline ? 2000 : 120)}
    style={[s.input, multiline && { minHeight: 100, textAlignVertical: 'top' }, !editable && { backgroundColor: '#EEF3F1' }]} /></View>;
}
export function Select({ label, value, options, onChange }) {
  return <View style={s.field}><Label style={s.fieldLabel}>{label}</Label><View style={s.select}><Picker accessibilityLabel={label} selectedValue={value} onValueChange={onChange} style={{ color: colors.ink }}>{options.map(o => <Picker.Item key={o.value} label={o.label} value={o.value} />)}</Picker></View></View>;
}
export function Choices({ label, value, options, onChange }) {
  return <View style={s.field}><Label style={s.fieldLabel}>{label}</Label>{options.map(o => <Pressable key={o.value} accessibilityRole="radio" accessibilityState={{ checked: o.value === value }} accessibilityLabel={o.label}
    onPress={() => onChange(o.value)} style={s.choice}><Ionicons name={o.value === value ? 'radio-button-on' : 'radio-button-off'} color={o.value === value ? colors.green : colors.muted} size={23} /><Label>{o.label}</Label></Pressable>)}</View>;
}
export function Row({ label, value, testID }) {
  const { width } = useWindowDimensions();
  const stacked = width < 320 || String(value || '').length > 32;
  return <View testID={testID} collapsable={!testID} style={[s.row, stacked && { flexDirection: 'column', gap: 4 }]}><Label muted style={!stacked && { flex: 1 }}>{label}</Label><Label style={!stacked && { flex: 1, textAlign: 'right' }}>{value || '—'}</Label></View>;
}
export function Section({ title, children }) { return <View style={s.section}>{title && <Heading>{title}</Heading>}{children}</View>; }
export function Status({ value }) {
  const color = value === '동행 완료' ? colors.green : value === '신청 취소' ? colors.red : ['접수 완료', '배정 대기'].includes(value) ? colors.amber : colors.blue;
  return <Label style={{ color, fontWeight: '700', fontSize: 15 }}>{value}</Label>;
}
export const s = StyleSheet.create({
  text: { fontSize: 17, lineHeight: 25, color: colors.ink, letterSpacing: 0 },
  heading: { fontSize: 21, lineHeight: 29, fontWeight: '700', color: colors.ink, marginBottom: 14 },
  button: { minHeight: 52, paddingVertical: 12, paddingHorizontal: 16, backgroundColor: colors.green, borderRadius: 6, flexDirection: 'row', gap: 9, alignItems: 'center', justifyContent: 'center', marginVertical: 5 },
  secondary: { backgroundColor: 'white', borderWidth: 1, borderColor: colors.line },
  buttonText: { fontSize: 17, lineHeight: 24, fontWeight: '700', color: 'white', flexShrink: 1, textAlign: 'center' },
  field: { marginBottom: 16 }, fieldLabel: { marginBottom: 7, fontWeight: '600' },
  input: { minHeight: 52, padding: 12, borderWidth: 1, borderColor: colors.line, borderRadius: 6, fontSize: 17, color: colors.ink, backgroundColor: 'white', letterSpacing: 0 },
  select: { borderWidth: 1, borderColor: colors.line, borderRadius: 6, backgroundColor: 'white', minHeight: 54 },
  choice: { flexDirection: 'row', alignItems: 'center', gap: 10, minHeight: 48 },
  row: { flexDirection: 'row', gap: 12, paddingVertical: 9, alignItems: 'flex-start' },
  section: { paddingVertical: 20, borderBottomWidth: 1, borderColor: colors.line },
  card: { borderRadius: 8, borderWidth: 1, borderColor: colors.line, padding: 16, backgroundColor: 'white', marginBottom: 12 },
});
