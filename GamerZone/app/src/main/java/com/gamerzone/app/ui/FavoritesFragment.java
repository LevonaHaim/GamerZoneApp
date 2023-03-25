package com.gamerzone.app.ui;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.core.view.MenuHost;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.gamerzone.app.R;
import com.gamerzone.app.model.Game;
import com.gamerzone.app.model.Genre;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class FavoritesFragment extends Fragment {
    FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
    DatabaseReference favoritesReference = FirebaseDatabase.getInstance()
            .getReference("users/" + currentUser.getUid() + "/favorites");
    ValueEventListener valueEventListener;
    ArrayList<Game> games;
    GamesAdapter adapter;
    MenuProvider menuProvider;
    boolean searchingEnabled = true;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        games = new ArrayList<>();

        // Listen to the favorite game list in Firebase under the current user and update the Recyclerview
        valueEventListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                ArrayList<Game> updatedGames = new ArrayList<>();
                for (DataSnapshot data : dataSnapshot.getChildren()) {
                    Game game = data.getValue(Game.class);
                    if (game != null) {
                        updatedGames.add(game);
                    }
                }
                games = updatedGames;
                adapter.updateDataSet(games);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        };
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_favorites, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Create the MenuProvider for the Toolbar menu
        menuProvider = new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
                menuInflater.inflate(R.menu.menu_toolbar_favorites, menu);
                SearchView searchView = (SearchView) menu.findItem(R.id.action_search).getActionView();

                // Search filtering logic, get the updated text from the search view and filter the game list
                SearchView.OnQueryTextListener queryTextListener = new SearchView.OnQueryTextListener() {
                    @Override
                    public boolean onQueryTextSubmit(String query) {
                        return false;
                    }

                    @Override
                    public boolean onQueryTextChange(String newText) {
                        if (searchingEnabled) {
                            filterGamesBySearch(newText);
                        }
                        return false;
                    }
                };
                searchView.setOnQueryTextListener(queryTextListener);
                searchView.setOnSearchClickListener(v -> searchingEnabled = true);
            }

            @SuppressLint("NonConstantResourceId")
            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
                // In case of click on "Logout" icon, sign out from FirebaseAuth
                // and navigate back to the Intro screen and pop the backstack the disallow user going back the previews destinations
                if (menuItem.getItemId() == R.id.action_logout) {
                    FirebaseAuth.getInstance().signOut();
                    NavController navController = NavHostFragment.findNavController(requireParentFragment());
                    navController.popBackStack();
                    navController.navigate(R.id.introFragment);
                }
                return true;
            }
        };

        // Add the MenuProvider to the Toolbar
        ((MenuHost) requireActivity()).addMenuProvider(menuProvider);

        RecyclerView rv = view.findViewById(R.id.favorites_games_rv);

        adapter = new GamesAdapter(games, new GamesAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(Game item) {
                // Put the needed data for the game details screen
                Bundle data = new Bundle();
                data.putString("id", item.getId());
                data.putString("name", item.getName());
                data.putString("release_date", item.getReleaseDate());
                data.putString("rating", item.getRating().toString());
                data.putString("image_url", item.getImageUrl());

                if (item.getGenres() != null) {
                    ArrayList<String> genres = new ArrayList<>();
                    for (Genre genre : item.getGenres()) {
                        genres.add(genre.getName());
                    }
                    data.putStringArrayList("genres", genres);
                }

                NavHostFragment.findNavController(requireParentFragment())
                        .navigate(R.id.gameDetailsFragment, data);
            }

            @Override
            public void onLongPress(Game item) {
                // On long click on a game from the RecyclerView, show an AlertDialog to propose item deletion
                AlertDialog alertDialog = new AlertDialog.Builder(requireContext())
                        .setTitle("Do you want to remove this game from Favorites?")
                        .setNegativeButton("No", (dialog, which) -> dialog.dismiss())
                        .setPositiveButton("Yes", (dialog, which) -> {
                            // In case of clicking on positive button, delete the games from favorite list on Firebase
                            favoritesReference.child(item.getId()).removeValue()
                                    .addOnSuccessListener(unused -> Toast.makeText(requireContext(),
                                            "Game removed from Favorites", Toast.LENGTH_SHORT).show())
                                    .addOnFailureListener(e -> Toast.makeText(requireContext(),
                                            "Failure occurred, please check your internet connection and try again",
                                            Toast.LENGTH_SHORT).show());
                        })
                        .create();
                alertDialog.show();
            }
        });
        rv.setAdapter(adapter);

        RecyclerView.LayoutManager lm = new GridLayoutManager(requireContext(), 3);
        rv.setLayoutManager(lm);
    }

    @Override
    public void onStart() {
        super.onStart();
        favoritesReference.addValueEventListener(valueEventListener);
    }

    @Override
    public void onStop() {
        super.onStop();
        favoritesReference.removeEventListener(valueEventListener);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        ((MenuHost) requireActivity()).removeMenuProvider(menuProvider);
    }

    private void filterGamesBySearch(String searchedText) {
        ArrayList<Game> filteredList = new ArrayList<>();
        for (Game game : games) {
            if (game.getName().toLowerCase().contains(searchedText.toLowerCase())) {
                filteredList.add(game);
            }
        }
        adapter.updateDataSet(filteredList);
    }
}