use legions_ai::card::{Card, Unit};
type Board = [[Option<Card>;5];4];
fn c(name: Unit, player: u8, value: u8) -> Card { Card { name, player, top:value, right:value, bottom:value, left:value } }
fn put(b: &mut Board, bombs: &mut [[u8;5];4], card: Card, y: usize, x: usize) {
    let mut d1 = vec![]; let mut d2 = vec![];
    let p = card.player;
    if p == 1 { d1.push(card); } else { d2.push(card); }
    let mut n = Card::get_neighbours(b,y,x,card.name);
    assert!(Card::place_card(b,&mut d1,&mut d2,0,(y,x),p,bombs,&mut n));
}
#[test] fn one_captures_a() {
    let mut b: Board = Default::default(); b[0][1] = Some(c(Unit::Keeper,2,10));
    put(&mut b,&mut [[0;5];4],c(Unit::Keeper,1,1),0,0);
    assert_eq!(b[0][1].unwrap().player,1);
}
#[test] fn warden_defense_bonus_prevents_capture() {
    let mut b: Board = Default::default(); b[0][1] = Some(c(Unit::Warden,2,5));
    put(&mut b,&mut [[0;5];4],c(Unit::Lancer,1,6),0,0);
    assert_eq!(b[0][1].unwrap().player,2);
}
#[test] fn keeper_attacks_first_visible_card_at_range() {
    let mut b: Board = Default::default(); b[0][3] = Some(c(Unit::Keeper,2,3)); b[0][4] = Some(c(Unit::Keeper,2,2));
    put(&mut b,&mut [[0;5];4],c(Unit::Keeper,1,6),0,0);
    assert_eq!(b[0][3].unwrap().player,1); assert_eq!(b[0][4].unwrap().player,2);
}
#[test] fn siren_pulls_allies_and_detonates_crossed_bombs() {
    let mut b: Board = Default::default(); b[0][4] = Some(c(Unit::Keeper,1,8));
    let mut bombs = [[0;5];4]; bombs[0][2]=2; bombs[0][3]=1;
    put(&mut b,&mut bombs,c(Unit::Siren,1,2),0,0);
    assert!(b[0][4].is_none()); assert_eq!(b[0][1].unwrap().top,5);
    assert_eq!(bombs,[[0;5];4]);
}
#[test] fn titan_flips_axis_for_allies_too() {
    let mut b: Board = Default::default(); let mut a = c(Unit::Keeper,1,3); a.left=1; a.right=9; b[0][1]=Some(a);
    put(&mut b,&mut [[0;5];4],c(Unit::Titan,1,2),0,0);
    assert_eq!(b[0][1].unwrap().left,9); assert_eq!(b[0][1].unwrap().right,1);
}
#[test] fn saboteur_only_bombs_empty_neighbours() {
    let mut b: Board = Default::default(); b[0][1] = Some(c(Unit::Keeper,1,3)); let mut bombs = [[0;5];4];
    put(&mut b,&mut bombs,c(Unit::Saboteur,1,5),0,0);
    assert_eq!(bombs[0][1],0); assert_eq!(bombs[1][0],1);
}
#[test] fn ravager_improves_on_capture_and_being_captured() {
    let mut b: Board = Default::default(); b[0][1]=Some(c(Unit::Ravager,2,3));
    put(&mut b,&mut [[0;5];4],c(Unit::Ravager,1,5),0,0);
    assert_eq!(b[0][0].unwrap().top,6); assert_eq!(b[0][1].unwrap().top,4);
}
#[test] fn slayer_reverses_rank_comparison() {
    let mut b: Board = Default::default(); b[0][1]=Some(c(Unit::Keeper,2,8));
    put(&mut b,&mut [[0;5];4],c(Unit::Slayer,1,3),0,0);
    assert_eq!(b[0][1].unwrap().player,1);
}
#[test] fn same_includes_allied_swarm_with_its_actual_owner_bonus() {
    let mut b: Board = Default::default(); b[0][1]=Some(c(Unit::Swarm,1,4)); b[3][4]=Some(c(Unit::Swarm,1,4)); b[1][0]=Some(c(Unit::Keeper,2,5));
    put(&mut b,&mut [[0;5];4],c(Unit::Lancer,1,5),0,0);
    assert_eq!(b[1][0].unwrap().player,1);
}
#[test] fn same_starts_combo_but_does_not_enable_lancer_pierce() {
    let mut b: Board = Default::default(); b[0][1]=Some(c(Unit::Keeper,2,5)); b[1][0]=Some(c(Unit::Keeper,2,5)); b[0][2]=Some(c(Unit::Keeper,2,7));
    put(&mut b,&mut [[0;5];4],c(Unit::Lancer,1,5),0,0);
    assert_eq!(b[0][1].unwrap().player,1); assert_eq!(b[1][0].unwrap().player,1); assert_eq!(b[0][2].unwrap().player,2);
}
#[test] fn combo_continues_on_greater_rank() {
    let mut b: Board = Default::default(); b[0][1]=Some(c(Unit::Lancer,2,5)); b[1][0]=Some(c(Unit::Lancer,2,5)); b[0][2]=Some(c(Unit::Keeper,2,3));
    put(&mut b,&mut [[0;5];4],c(Unit::Lancer,1,5),0,0);
    assert_eq!(b[0][2].unwrap().player,1);
}
#[test] fn lancer_pierces_chain_and_stops_at_ally() {
    let mut b: Board = Default::default(); for x in 1..5 { b[0][x]=Some(c(Unit::Keeper,2,2)); }
    put(&mut b,&mut [[0;5];4],c(Unit::Lancer,1,6),0,0);
    for x in 1..5 { assert_eq!(b[0][x].unwrap().player,1); }
    let mut b: Board = Default::default(); b[0][1]=Some(c(Unit::Keeper,1,2)); b[0][2]=Some(c(Unit::Keeper,2,2));
    put(&mut b,&mut [[0;5];4],c(Unit::Lancer,1,6),0,0);
    assert_eq!(b[0][2].unwrap().player,2);
}
#[test] fn piercing_tie_does_not_join_same() {
    let mut b: Board = Default::default(); b[0][1]=Some(c(Unit::Keeper,2,2)); b[0][2]=Some(c(Unit::Keeper,2,6)); b[1][0]=Some(c(Unit::Keeper,2,6));
    put(&mut b,&mut [[0;5];4],c(Unit::Lancer,1,6),0,0);
    assert_eq!(b[0][1].unwrap().player,1); assert_eq!(b[0][2].unwrap().player,2); assert_eq!(b[1][0].unwrap().player,2);
}
#[test] fn invalid_coordinates_and_index_are_rejected_without_mutation() {
    let mut b: Board = Default::default(); let mut blue=vec![c(Unit::Keeper,1,3)]; let mut red=vec![];
    for (i,p,owner) in [(2,(0,0),1),(0,(4,0),1),(0,(0,5),1),(0,(0,0),3)] {
        assert!(!Card::place_card(&mut b,&mut blue,&mut red,i,p,owner,&mut [[0;5];4],&mut [None;4]));
    }
    assert_eq!(blue.len(),1); assert!(b.iter().flatten().all(Option::is_none));
}
