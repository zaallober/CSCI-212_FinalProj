//
// okay, going to pivot the approach from treating OpInterpreter as a buffer mutator.
// This approach should be substantially cheaper too.
//
// It should be easier to bild a representation of the final file that is cheap and disposable, then project and back just the portions that we need with their referenced content.

//
// 1 [Original] <- Original file
// 2 [Original][Insert][Original] <- keeps original backed file location index.
// 3 [Original][Insert][Original][Original] <- delete splits any two pieces
// 4 [Original][Insert][Insert][Original][Original] <- this goes for insert pieces too.
// 5 [Original][Insert][Insert][Original][Write] <- Write overwrites offset regions in the piece table, overlapping etc, split,pop, whatever.
// 6 [Original][Insert][Insert][Original][Write][Fill] <- Offsets past logical EOF generate a fill piece that is subsequently partially or entirely overwritten
// 7 [Original][Insert][Insert][Original][Write][Write] <- Final piece table to be materialized with the backed portions, logical portions, offsets are final, preview seek should easily use this.
