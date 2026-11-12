use legions_ai::{card::{Card, Unit}, utility::calc_scores};

#[test]
fn decks_have_four_orientations_and_red_is_mirrored() {
    let mut blue = vec![];
    let mut red = vec![];
    Card::add_to_deck(&mut blue, Unit::Keeper, 1);
    Card::add_to_deck(&mut red, Unit::Keeper, 2);
    assert_eq!(blue.len(), 4);
    assert_eq!([blue[0].top, blue[0].right, blue[0].bottom, blue[0].left], [9,5,1,5]);
    assert_eq!([blue[1].top, blue[1].right, blue[1].bottom, blue[1].left], [5,9,5,1]);
    assert_eq!([red[1].top, red[1].right, red[1].bottom, red[1].left], [5,9,5,1]);
}

#[test]
fn blue_starts_with_bonus_and_captured_cards_count_for_current_owner() {
    let mut board: [[Option<Card>;5];4] = Default::default();
    assert_eq!(calc_scores(&board), (1,0));
    let mut deck = vec![];
    Card::add_to_deck(&mut deck, Unit::Warden, 2);
    board[0][0] = deck.pop();
    assert_eq!(calc_scores(&board), (1,1));
    board[0][0].as_mut().unwrap().player = 1;
    assert_eq!(calc_scores(&board), (2,0));
}

#[test]
fn bombs_stack_and_ranks_stay_in_bounds() {
    let mut board: [[Option<Card>;5];4] = Default::default();
    let mut bombs = [[0;5];4];
    let mut deck = vec![];
    Card::add_to_deck(&mut deck, Unit::Keeper, 1);
    board[0][0] = deck.pop();
    bombs[0][0] = 4;
    Card::bomb_check(&mut board, (0,0), &mut bombs);
    let card = board[0][0].as_mut().unwrap();
    assert_eq!(card.right, 1);
    card.upgrade(10);
    assert_eq!(card.right, 10);
    assert_eq!(bombs[0][0], 0);
}

#[test]
fn occupied_cell_does_not_consume_card() {
    let mut board: [[Option<Card>;5];4] = Default::default();
    let mut blue = vec![];
    let mut red = vec![];
    Card::add_to_deck(&mut blue, Unit::Warden, 1);
    Card::add_to_deck(&mut red, Unit::Titan, 2);
    let mut bombs = [[0;5];4];
    let mut neighbours = [None;4];
    assert!(Card::place_card(&mut board, &mut blue, &mut red, 0, (0,0), 1, &mut bombs, &mut neighbours));
    assert!(!Card::place_card(&mut board, &mut blue, &mut red, 0, (0,0), 2, &mut bombs, &mut neighbours));
    assert_eq!(red.len(), 4);
}
